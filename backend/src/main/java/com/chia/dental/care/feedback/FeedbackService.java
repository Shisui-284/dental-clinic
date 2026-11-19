package com.chia.dental.care.feedback;

import com.chia.dental.care.CareConstants;
import com.chia.dental.care.CareDtos.*;
import com.chia.dental.care.CareQueryRepository;
import com.chia.dental.care.support.AssigneeSelector;
import com.chia.dental.care.support.CareSettings;
import com.chia.dental.care.support.WorkingTimeCalculator;
import com.chia.dental.common.audit.AuditLogService;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.Complaint;
import com.chia.dental.entity.Feedback;
import com.chia.dental.entity.FeedbackToken;
import com.chia.dental.repository.ComplaintRepository;
import com.chia.dental.repository.FeedbackRepository;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Phản hồi/đánh giá (UC-CSKH-10, FR-30..33).
 * Khách gửi qua link token dùng một lần: mỗi lịch một đánh giá, không sửa sau khi gửi.
 * ≤3 sao hoặc có từ khóa triệu chứng => bật cảnh báo đỏ + có Complaint (trigger DB tạo cho ≤3 sao;
 * ca >3 sao nhưng có từ khóa thì ứng dụng tạo) giao lễ tân và hạn xử lý 48h làm việc.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final String LINK_INVALID_MSG = "Liên kết đã được sử dụng hoặc không còn hiệu lực.";

    private final FeedbackTokenService tokenService;
    private final FeedbackRepository feedbackRepository;
    private final ComplaintRepository complaintRepository;
    private final SymptomDetector symptomDetector;
    private final AssigneeSelector assigneeSelector;
    private final WorkingTimeCalculator workingTime;
    private final CareSettings config;
    private final CareQueryRepository queries;
    private final AuditLogService audit;
    private final JdbcTemplate jdbc;

    // ------------------------------------------------------------------ công khai (K9)

    @Transactional(readOnly = true)
    public FeedbackLinkInfo linkInfo(String rawToken) {
        FeedbackToken token = tokenService.peek(rawToken)
                .filter(t -> isUsable(t, OffsetDateTime.now(CareConstants.ZONE)))
                .orElseThrow(FeedbackService::linkInvalid);
        Map<String, Object> row = jdbc.queryForMap("""
            SELECT a.date_time, d.full_name AS doctor_name,
                   COALESCE((SELECT string_agg(s.name, ', ' ORDER BY s.name) FROM appointment_service aps
                              JOIN service s ON s.service_id = aps.service_id
                             WHERE aps.appointment_id = a.appointment_id), '') AS services
              FROM appointment a JOIN staff_account d ON d.staff_id = a.staff_id
             WHERE a.appointment_id = ?
            """, token.getAppointmentId());
        OffsetDateTime time = ((java.sql.Timestamp) row.get("date_time")).toInstant()
                .atZone(CareConstants.ZONE).toOffsetDateTime();
        return new FeedbackLinkInfo(config.getString("clinic_name", "Phòng khám nha khoa CHIA"),
                config.getString("clinic_hotline", "1900 1234"), time,
                String.valueOf(row.get("doctor_name")), String.valueOf(row.get("services")));
    }

    @Transactional
    public SubmitFeedbackResponse submit(String rawToken, int rating, String comment) {
        OffsetDateTime now = OffsetDateTime.now(CareConstants.ZONE);
        FeedbackToken token = tokenService.findForUpdate(rawToken)
                .filter(t -> isUsable(t, now))
                .orElseThrow(FeedbackService::linkInvalid);

        Map<String, Object> appt = jdbc.queryForMap("""
            SELECT a.status, a.staff_id, a.patient_id, p.customer_id
              FROM appointment a JOIN patient_profile p ON p.patient_id = a.patient_id
             WHERE a.appointment_id = ?""", token.getAppointmentId());
        if (!"COMPLETED".equals(appt.get("status"))) {
            throw new BusinessException("APPOINTMENT_NOT_COMPLETED", "Chỉ đánh giá được buổi khám đã hoàn tất.", HttpStatus.CONFLICT);
        }
        if (feedbackRepository.existsByAppointmentId(token.getAppointmentId())) {
            throw linkInvalid();
        }

        String cleanComment = comment == null || comment.isBlank() ? null : comment.trim();
        boolean symptom = symptomDetector.detect(cleanComment);
        boolean alert = rating <= 3 || symptom;
        long customerId = ((Number) appt.get("customer_id")).longValue();
        long patientId = ((Number) appt.get("patient_id")).longValue();

        Feedback fb = feedbackRepository.save(Feedback.builder()
                .appointmentId(token.getAppointmentId()).customerId(customerId).patientId(patientId)
                .staffId(((Number) appt.get("staff_id")).longValue())
                .ratingScore((short) rating).comment(cleanComment)
                .alertFlag(alert).isHidden(false).createdAt(now).build());
        token.setUsedAt(now);

        if (alert) {
            raiseAlert(fb, symptom, now);
        }
        audit.logCustomer(customerId, "FEEDBACK_SUBMIT", "feedback", fb.getFeedbackId(), null,
                Map.of("rating", rating, "alert", alert));

        String msg = alert && rating <= 3
                ? "Cảm ơn quý khách. Phòng khám sẽ liên hệ trong 48h."
                : "Cảm ơn quý khách đã đánh giá.";
        return new SubmitFeedbackResponse(alert, msg);
    }

    /**
     * Bảo đảm có Complaint cho ca cảnh báo, giao lễ tân (vòng tròn), đặt hạn 48h làm việc và mức độ.
     * Trigger DB đã tự tạo Complaint cho ≤3 sao; ca có từ khóa nhưng >3 sao thì tạo tại đây.
     */
    private void raiseAlert(Feedback fb, boolean symptom, OffsetDateTime now) {
        Complaint c = complaintRepository.findByFeedbackId(fb.getFeedbackId()).orElseGet(() ->
                Complaint.builder().patientId(fb.getPatientId()).feedbackId(fb.getFeedbackId())
                        .content(fb.getComment() == null ? "Đánh giá " + fb.getRatingScore() + " sao" : fb.getComment())
                        .status(CareConstants.CMP_OPEN).isHidden(false).createdAt(now).severity("MEDIUM").build());
        c.setSeverity(symptom ? "HIGH" : (fb.getRatingScore() <= 2 ? "MEDIUM" : "LOW"));
        if (c.getHandlerId() == null) c.setHandlerId(assigneeSelector.nextForComplaint());
        c.setSlaDeadline(workingTime.addWorkingHours(now, config.getInt("care_sla_hours", 48)));
        complaintRepository.save(c);
    }

    // ------------------------------------------------------------------ nhân sự

    /** Bác sĩ chỉ thấy phản hồi bệnh nhân mình khám; chỉ Quản lý thấy phản hồi đã ẩn (FR-33). */
    @Transactional(readOnly = true)
    public PageResult<FeedbackView> list(UserPrincipal actor, boolean alertOnly, boolean includeHidden, int page, int size) {
        boolean doctor = CareConstants.ROLE_DOCTOR.equals(actor.getRole());
        boolean manager = CareConstants.ROLE_MANAGER.equals(actor.getRole());
        return queries.listFeedback(doctor ? actor.getId() : null, alertOnly, manager && includeHidden, null,
                Math.max(0, page), Math.min(Math.max(size, 1), 100));
    }

    /** Chỉ Quản lý được ẩn, bắt buộc lý do, ghi audit; không xóa vật lý. */
    @Transactional
    public void hide(long feedbackId, UserPrincipal actor, String reason, boolean hide) {
        Feedback fb = feedbackRepository.findById(feedbackId).orElseThrow(() ->
                new BusinessException("FEEDBACK_NOT_FOUND", "Không tìm thấy phản hồi.", HttpStatus.NOT_FOUND));
        if (Boolean.TRUE.equals(fb.getIsHidden()) == hide) {
            throw new BusinessException("NO_CHANGE", hide ? "Phản hồi đã được ẩn." : "Phản hồi đang hiển thị.", HttpStatus.CONFLICT);
        }
        fb.setIsHidden(hide);
        fb.setHiddenBy(hide ? actor.getId() : null);
        fb.setHiddenAt(hide ? OffsetDateTime.now(CareConstants.ZONE) : null);
        feedbackRepository.save(fb);
        audit.logStaff(actor.getId(), hide ? "FEEDBACK_HIDE" : "FEEDBACK_UNHIDE", "feedback", feedbackId,
                Map.of("hidden", !hide), Map.of("hidden", hide, "reason", reason));
    }

    private static boolean isUsable(FeedbackToken t, OffsetDateTime now) {
        return t.getUsedAt() == null && t.getExpiresAt().isAfter(now);
    }

    private static BusinessException linkInvalid() {
        return new BusinessException("FEEDBACK_LINK_INVALID", LINK_INVALID_MSG, HttpStatus.GONE);
    }
}
