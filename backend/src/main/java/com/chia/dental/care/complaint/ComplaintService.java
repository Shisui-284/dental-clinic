package com.chia.dental.care.complaint;

import com.chia.dental.care.CareConstants;
import com.chia.dental.care.CareDtos.*;
import com.chia.dental.care.CareQueryRepository;
import com.chia.dental.care.support.AssigneeSelector;
import com.chia.dental.care.support.CareSettings;
import com.chia.dental.care.support.WorkingTimeCalculator;
import com.chia.dental.common.audit.AuditLogService;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.Complaint;
import com.chia.dental.repository.ComplaintRepository;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Khiếu nại (FR-32): Mở → Đang xử lý → Chờ phản hồi → Đóng.
 * Không nhảy từ Mở sang Đóng; đóng bắt buộc Result và Action_Taken; không xóa, chỉ Quản lý ẩn kèm lý do.
 */
@Service
@RequiredArgsConstructor
public class ComplaintService {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            CareConstants.CMP_OPEN, Set.of(CareConstants.CMP_IN_PROGRESS),
            CareConstants.CMP_IN_PROGRESS, Set.of(CareConstants.CMP_WAITING_RESPONSE, CareConstants.CMP_CLOSED),
            CareConstants.CMP_WAITING_RESPONSE, Set.of(CareConstants.CMP_IN_PROGRESS, CareConstants.CMP_CLOSED),
            CareConstants.CMP_CLOSED, Set.of());

    private final ComplaintRepository repository;
    private final CareQueryRepository queries;
    private final AssigneeSelector assigneeSelector;
    private final WorkingTimeCalculator workingTime;
    private final CareSettings config;
    private final AuditLogService audit;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public PageResult<ComplaintView> list(UserPrincipal actor, String status, boolean includeHidden, int page, int size) {
        boolean doctor = CareConstants.ROLE_DOCTOR.equals(actor.getRole());
        boolean manager = CareConstants.ROLE_MANAGER.equals(actor.getRole());
        PageResult<ComplaintView> r = queries.listComplaints(
                status == null || status.isBlank() ? null : status.trim().toUpperCase(),
                manager && includeHidden, doctor ? actor.getId() : null,
                Math.max(0, page), Math.min(Math.max(size, 1), 100));
        return doctor ? new PageResult<>(r.items().stream().map(ComplaintService::withoutPhone).toList(), r.total(), r.page(), r.size()) : r;
    }

    @Transactional(readOnly = true)
    public ComplaintView get(UserPrincipal actor, long id) {
        if (CareConstants.ROLE_DOCTOR.equals(actor.getRole()) && !queries.complaintBelongsToDoctor(id, actor.getId())) {
            throw new BusinessException("FORBIDDEN_SCOPE", "Bạn chỉ xem được khiếu nại thuộc bệnh nhân bạn trực tiếp khám.", HttpStatus.FORBIDDEN);
        }
        ComplaintView v = queries.findComplaint(id).orElseThrow(ComplaintService::notFound);
        return CareConstants.ROLE_DOCTOR.equals(actor.getRole()) ? withoutPhone(v) : v;
    }

    /** Ghi nhận khiếu nại do khách phản ánh trực tiếp (không qua đánh giá). */
    @Transactional
    public ComplaintView create(UserPrincipal actor, CreateComplaintRequest req) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM patient_profile WHERE patient_id = ?", Integer.class, req.patientId());
        if (exists == null || exists == 0) {
            throw new BusinessException("PATIENT_NOT_FOUND", "Không tìm thấy hồ sơ bệnh nhân.", HttpStatus.NOT_FOUND);
        }
        if (req.feedbackId() != null && repository.findByFeedbackId(req.feedbackId()).isPresent()) {
            throw new BusinessException("COMPLAINT_EXISTS", "Phản hồi này đã có khiếu nại đi kèm.", HttpStatus.CONFLICT);
        }
        String severity = req.severity() == null || req.severity().isBlank() ? "MEDIUM" : req.severity().trim().toUpperCase();
        if (!Set.of("LOW", "MEDIUM", "HIGH").contains(severity)) {
            throw new BusinessException("INVALID_SEVERITY", "Mức độ phải là LOW, MEDIUM hoặc HIGH.", HttpStatus.BAD_REQUEST);
        }
        OffsetDateTime now = OffsetDateTime.now(CareConstants.ZONE);
        Complaint c = repository.save(Complaint.builder()
                .patientId(req.patientId()).feedbackId(req.feedbackId()).content(req.content().trim())
                .severity(severity).handlerId(assigneeSelector.nextForComplaint()).status(CareConstants.CMP_OPEN)
                .slaDeadline(workingTime.addWorkingHours(now, config.getInt("care_sla_hours", 48)))
                .isHidden(false).createdAt(now).build());
        audit.logStaff(actor.getId(), "COMPLAINT_CREATE", "complaint", c.getComplaintId(), null,
                Map.of("severity", severity, "handlerId", c.getHandlerId()));
        return queries.findComplaint(c.getComplaintId()).orElseThrow(ComplaintService::notFound);
    }

    @Transactional
    public ComplaintView changeStatus(UserPrincipal actor, long id, ComplaintStatusRequest req) {
        Complaint c = repository.findById(id).orElseThrow(ComplaintService::notFound);
        String target = req.status().trim().toUpperCase();
        if (!TRANSITIONS.containsKey(target)) {
            throw new BusinessException("INVALID_STATUS", "Trạng thái không hợp lệ: " + target, HttpStatus.BAD_REQUEST);
        }
        if (!TRANSITIONS.get(c.getStatus()).contains(target)) {
            throw new BusinessException("INVALID_COMPLAINT_TRANSITION",
                    "Không thể chuyển khiếu nại từ " + c.getStatus() + " sang " + target
                            + (CareConstants.CMP_OPEN.equals(c.getStatus()) && CareConstants.CMP_CLOSED.equals(target)
                            ? " (không được đóng trực tiếp từ Mở)" : "."), HttpStatus.CONFLICT);
        }
        Map<String, Object> old = Map.of("status", c.getStatus());
        if (req.actionTaken() != null && !req.actionTaken().isBlank()) c.setActionTaken(req.actionTaken().trim());
        if (req.result() != null && !req.result().isBlank()) c.setResult(req.result().trim());

        if (CareConstants.CMP_CLOSED.equals(target)) {
            if (isBlank(c.getResult()) || isBlank(c.getActionTaken())) {
                throw new BusinessException("CLOSE_REQUIRES_RESULT",
                        "Đóng khiếu nại bắt buộc có Kết quả (result) và Biện pháp đã thực hiện (actionTaken).", HttpStatus.BAD_REQUEST);
            }
            c.setClosedAt(OffsetDateTime.now(CareConstants.ZONE));
        }
        if (CareConstants.CMP_IN_PROGRESS.equals(target) && CareConstants.CMP_OPEN.equals(c.getStatus())) {
            c.setHandlerId(actor.getId()); // người nhận xử lý trở thành người phụ trách
        }
        c.setStatus(target);
        repository.save(c);
        audit.logStaff(actor.getId(), "COMPLAINT_STATUS", "complaint", id, old, Map.of("status", target));
        return queries.findComplaint(id).orElseThrow(ComplaintService::notFound);
    }

    @Transactional
    public void hide(UserPrincipal actor, long id, String reason, boolean hide) {
        Complaint c = repository.findById(id).orElseThrow(ComplaintService::notFound);
        if (Boolean.TRUE.equals(c.getIsHidden()) == hide) {
            throw new BusinessException("NO_CHANGE", hide ? "Khiếu nại đã được ẩn." : "Khiếu nại đang hiển thị.", HttpStatus.CONFLICT);
        }
        c.setIsHidden(hide);
        c.setHiddenBy(hide ? actor.getId() : null);
        c.setHiddenAt(hide ? OffsetDateTime.now(CareConstants.ZONE) : null);
        repository.save(c);
        audit.logStaff(actor.getId(), hide ? "COMPLAINT_HIDE" : "COMPLAINT_UNHIDE", "complaint", id,
                Map.of("hidden", !hide), Map.of("hidden", hide, "reason", reason));
    }

    private static ComplaintView withoutPhone(ComplaintView v) {
        return new ComplaintView(v.complaintId(), v.patientId(), v.patientName(), null, v.feedbackId(), v.ratingScore(),
                v.content(), v.severity(), v.handlerId(), v.handlerName(), v.actionTaken(), v.result(), v.status(),
                v.slaDeadline(), v.overdue(), v.hidden(), v.createdAt(), v.closedAt());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static BusinessException notFound() {
        return new BusinessException("COMPLAINT_NOT_FOUND", "Không tìm thấy khiếu nại.", HttpStatus.NOT_FOUND);
    }
}
