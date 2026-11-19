package com.chia.dental.care.task;

import com.chia.dental.care.CareConstants;
import com.chia.dental.care.CareDtos.*;
import com.chia.dental.care.CareQueryRepository;
import com.chia.dental.care.notification.NotificationJobService;
import com.chia.dental.care.notification.NotificationType;
import com.chia.dental.care.notification.OptOutPolicy;
import com.chia.dental.care.support.WorkingTimeCalculator;
import com.chia.dental.common.audit.AuditLogService;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.CareTask;
import com.chia.dental.repository.CareTaskRepository;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Xử lý công việc CSKH (UC-CSKH-07/08/09, FR-24/37).
 * Vòng đời: WAITING → IN_PROGRESS → NEEDS_MEDICAL → (bác sĩ phản hồi) IN_PROGRESS → CLOSED.
 * Đóng bắt buộc Result_Note; "Quá hạn" chỉ là badge, không tự đóng, không chặn thao tác.
 */
@Service
@RequiredArgsConstructor
public class CareTaskService {

    private final CareTaskRepository taskRepository;
    private final CareQueryRepository queries;
    private final NotificationJobService jobService;
    private final OptOutPolicy optOutPolicy;
    private final WorkingTimeCalculator workingTime;
    private final AuditLogService audit;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public PageResult<CareTaskView> list(String status, String type, Boolean overdue, Long assigneeId,
                                         boolean includeNotYetDue, int page, int size) {
        return queries.listTasks(norm(status), norm(type), overdue, assigneeId, null, !includeNotYetDue,
                Math.max(0, page), clampSize(size));
    }

    @Transactional(readOnly = true)
    public CareTaskView get(long taskId) {
        return queries.findTask(taskId).orElseThrow(() -> notFound());
    }

    @Transactional(readOnly = true)
    public List<CareTaskView> medicalSupportTasksFor(UserPrincipal doctor) {
        return queries.medicalSupportTasks(doctor.getId());
    }

    /** Lễ tân nhận xử lý: WAITING → IN_PROGRESS (người bấm trở thành người phụ trách). */
    @Transactional
    public CareTaskView start(long taskId, UserPrincipal actor) {
        CareTask t = load(taskId);
        requireStatus(t, CareConstants.TASK_WAITING, "Chỉ nhận xử lý việc đang ở trạng thái Chờ chăm sóc.");
        Map<String, Object> old = snapshot(t);
        t.setStatus(CareConstants.TASK_IN_PROGRESS);
        t.setAssigneeId(actor.getId());
        taskRepository.save(t);
        audit.logStaff(actor.getId(), "CARE_TASK_START", "care_task", taskId, old, snapshot(t));
        return get(taskId);
    }

    /** IN_PROGRESS → NEEDS_MEDICAL: cần bác sĩ hỗ trợ chuyên môn (ngoài phạm vi trả lời của lễ tân). */
    @Transactional
    public CareTaskView requestMedicalSupport(long taskId, UserPrincipal actor, String note) {
        CareTask t = load(taskId);
        requireStatus(t, CareConstants.TASK_IN_PROGRESS, "Chỉ chuyển Cần hỗ trợ chuyên môn từ trạng thái Đang xử lý.");
        if (t.getAppointmentId() == null) {
            throw new BusinessException("NO_DOCTOR_CONTEXT",
                    "Việc này không gắn với lịch hẹn nên không xác định được bác sĩ phụ trách.", HttpStatus.CONFLICT);
        }
        Map<String, Object> old = snapshot(t);
        t.setStatus(CareConstants.TASK_NEEDS_MEDICAL);
        t.setMedicalRequestNote(note.trim());
        taskRepository.save(t);
        audit.logStaff(actor.getId(), "CARE_TASK_REQUEST_MEDICAL", "care_task", taskId, old, snapshot(t));
        return get(taskId);
    }

    /** Bác sĩ phụ trách ca đó (hoặc quản lý) phản hồi: NEEDS_MEDICAL → IN_PROGRESS. */
    @Transactional
    public CareTaskView medicalResponse(long taskId, UserPrincipal actor, String note) {
        CareTask t = load(taskId);
        requireStatus(t, CareConstants.TASK_NEEDS_MEDICAL, "Việc không ở trạng thái Cần hỗ trợ chuyên môn.");
        if (CareConstants.ROLE_DOCTOR.equals(actor.getRole())) {
            List<Long> doctor = jdbc.queryForList("SELECT staff_id FROM appointment WHERE appointment_id = ?",
                    Long.class, t.getAppointmentId());
            if (doctor.isEmpty() || !doctor.get(0).equals(actor.getId())) {
                throw new BusinessException("FORBIDDEN_SCOPE", "Bạn chỉ phản hồi được việc thuộc bệnh nhân bạn phụ trách.",
                        HttpStatus.FORBIDDEN);
            }
        }
        Map<String, Object> old = snapshot(t);
        t.setStatus(CareConstants.TASK_IN_PROGRESS);
        t.setMedicalResponseNote(note.trim());
        taskRepository.save(t);
        audit.logStaff(actor.getId(), "CARE_TASK_MEDICAL_RESPONSE", "care_task", taskId, old, snapshot(t));
        return get(taskId);
    }

    /** IN_PROGRESS → CLOSED, bắt buộc Result_Note. */
    @Transactional
    public CareTaskView close(long taskId, UserPrincipal actor, String resultNote) {
        CareTask t = load(taskId);
        requireStatus(t, CareConstants.TASK_IN_PROGRESS,
                "Chỉ đóng việc đang ở trạng thái Đang xử lý (việc Cần hỗ trợ chuyên môn phải có phản hồi của bác sĩ trước).");
        if (resultNote == null || resultNote.isBlank()) {
            throw new BusinessException("RESULT_NOTE_REQUIRED", "Bắt buộc nhập kết quả xử lý (Result_Note) khi đóng việc.",
                    HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> old = snapshot(t);
        t.setStatus(CareConstants.TASK_CLOSED);
        t.setResultNote(resultNote.trim());
        t.setCompletedAt(OffsetDateTime.now(CareConstants.ZONE));
        taskRepository.save(t);
        audit.logStaff(actor.getId(), "CARE_TASK_CLOSE", "care_task", taskId, old, snapshot(t));
        return get(taskId);
    }

    /**
     * Lễ tân bấm "Gửi tin mời tái khám" cho việc RECALL (UC-CSKH-05). Hệ thống chỉ gửi tin mời,
     * KHÔNG tự tạo lịch hẹn. Khách opt-out -> từ chối, lễ tân gọi điện thủ công.
     */
    @Transactional
    public CareTaskView sendRecallInvitation(long taskId, UserPrincipal actor) {
        CareTask t = load(taskId);
        if (!CareConstants.TYPE_RECALL.equals(t.getTaskType())) {
            throw new BusinessException("NOT_RECALL_TASK", "Chỉ gửi tin mời tái khám cho việc loại Nhắc tái khám.", HttpStatus.CONFLICT);
        }
        if (CareConstants.TASK_CLOSED.equals(t.getStatus())) {
            throw new BusinessException("TASK_CLOSED", "Việc đã đóng.", HttpStatus.CONFLICT);
        }
        long customerId = jdbc.queryForObject("SELECT customer_id FROM patient_profile WHERE patient_id = ?", Long.class, t.getPatientId());
        if (optOutPolicy.isBlockedEverywhere(customerId, NotificationType.RECALL)) {
            throw new BusinessException("CUSTOMER_OPTED_OUT",
                    "Khách hàng đã từ chối nhận tin. Vui lòng liên hệ bằng cuộc gọi trực tiếp.", HttpStatus.CONFLICT);
        }
        if (jobService.hasActiveJob(taskId, NotificationType.RECALL)) {
            throw new BusinessException("INVITATION_ALREADY_SENT", "Tin mời tái khám đã được gửi hoặc đang chờ gửi.", HttpStatus.CONFLICT);
        }
        int n = jobService.countJobs(taskId, NotificationType.RECALL) + 1;
        jobService.enqueue("RECALL:" + taskId + ":" + n, NotificationType.RECALL, customerId, t.getPatientId(),
                t.getAppointmentId(), taskId, null, OffsetDateTime.now(CareConstants.ZONE));
        audit.logStaff(actor.getId(), "CARE_RECALL_INVITE", "care_task", taskId, null, Map.of("attempt", n));
        return get(taskId);
    }

    /**
     * Ghi nhận lễ tân đã gọi xác nhận lịch sắp đến (UC-CSKH-03/FR-23). Tạo việc CONFIRM_CALL (MANUAL) đã đóng,
     * để lịch hiển thị "Đã gọi xác nhận" và có dấu vết.
     */
    @Transactional
    public CareTaskView recordConfirmCall(long appointmentId, UserPrincipal actor, String resultNote) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT status, patient_id, date_time FROM appointment WHERE appointment_id = ?""", appointmentId);
        if (rows.isEmpty()) {
            throw new BusinessException("APPOINTMENT_NOT_FOUND", "Không tìm thấy lịch hẹn.", HttpStatus.NOT_FOUND);
        }
        if (!"CONFIRMED".equals(rows.get(0).get("status"))) {
            throw new BusinessException("APPOINTMENT_NOT_CONFIRMED", "Chỉ ghi nhận gọi xác nhận cho lịch Đã xác nhận.", HttpStatus.CONFLICT);
        }
        long patientId = ((Number) rows.get(0).get("patient_id")).longValue();
        OffsetDateTime now = OffsetDateTime.now(CareConstants.ZONE);
        CareTask t = taskRepository.save(CareTask.builder()
                .appointmentId(appointmentId).patientId(patientId)
                .taskType(CareConstants.TYPE_CONFIRM_CALL).source(CareConstants.SOURCE_MANUAL)
                .assigneeId(actor.getId()).createdAt(now).scheduledAt(now).visibleFrom(now).slaDeadline(now)
                .status(CareConstants.TASK_CLOSED).isOverdue(false)
                .resultNote(resultNote.trim()).completedAt(now).build());
        audit.logStaff(actor.getId(), "CARE_CONFIRM_CALL", "care_task", t.getTaskId(), null, snapshot(t));
        return get(t.getTaskId());
    }

    // ------------------------------------------------------------------ định kỳ

    /** Đánh dấu quá hạn SLA: Chờ chăm sóc / Cần hỗ trợ chuyên môn đã qua sla_deadline (SLA vẫn chạy ở trạng thái chuyên môn). */
    @Transactional
    public int flagOverdue() {
        return jdbc.update("""
            UPDATE care_task SET is_overdue = TRUE
             WHERE NOT is_overdue AND status IN ('WAITING','NEEDS_MEDICAL') AND sla_deadline < now()
            """);
    }

    // ------------------------------------------------------------------ helper

    private CareTask load(long taskId) {
        return taskRepository.findById(taskId).orElseThrow(CareTaskService::notFound);
    }

    private static BusinessException notFound() {
        return new BusinessException("CARE_TASK_NOT_FOUND", "Không tìm thấy công việc CSKH.", HttpStatus.NOT_FOUND);
    }

    private static void requireStatus(CareTask t, String expected, String message) {
        if (!expected.equals(t.getStatus())) {
            throw new BusinessException("INVALID_TASK_TRANSITION", message, HttpStatus.CONFLICT);
        }
    }

    private static Map<String, Object> snapshot(CareTask t) {
        return Map.of("status", t.getStatus(), "assigneeId", t.getAssigneeId());
    }

    private static String norm(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
