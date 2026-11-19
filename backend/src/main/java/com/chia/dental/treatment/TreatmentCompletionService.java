package com.chia.dental.treatment;

import com.chia.dental.care.CareConstants;
import com.chia.dental.common.audit.AuditLogService;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * BẢN TỐI THIỂU của UC-14 "Hoàn tất khám" — chỉ để có điểm kích hoạt CSKH (SRS 3.2.1, FR-24).
 * Bác sĩ phụ trách ghi kết quả điều trị, ngày tái khám đề xuất (nếu có) và hoàn tất lịch đang ở trạng thái Đã đến.
 * Phần còn lại của UC-14 (tiến trình liệu trình, xác nhận cảnh báo đỏ, đơn thuốc...) thuộc phân hệ hồ sơ/điều trị.
 */
@Service
@RequiredArgsConstructor
public class TreatmentCompletionService {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final ApplicationEventPublisher events;
    private final AuditLogService audit;

    public record CompleteRequest(String treatmentResult, String clinicalNote, LocalDate nextAppointmentDate,
                                  List<Long> performedServiceIds) {}

    @Transactional
    public void complete(long appointmentId, UserPrincipal actor, CompleteRequest req) {
        if (req.treatmentResult() == null || req.treatmentResult().isBlank()) {
            throw new BusinessException("TREATMENT_RESULT_REQUIRED", "Bắt buộc nhập kết quả điều trị trước khi hoàn tất.", HttpStatus.BAD_REQUEST);
        }
        if (req.nextAppointmentDate() != null && !req.nextAppointmentDate().isAfter(LocalDate.now(CareConstants.ZONE))) {
            throw new BusinessException("INVALID_NEXT_DATE", "Ngày tái khám đề xuất phải sau hôm nay.", HttpStatus.BAD_REQUEST);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status, staff_id, patient_id FROM appointment WHERE appointment_id = ? FOR UPDATE", appointmentId);
        if (rows.isEmpty()) {
            throw new BusinessException("APPOINTMENT_NOT_FOUND", "Không tìm thấy lịch hẹn.", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> a = rows.get(0);
        if (CareConstants.ROLE_DOCTOR.equals(actor.getRole()) && ((Number) a.get("staff_id")).longValue() != actor.getId()) {
            throw new BusinessException("FORBIDDEN_SCOPE", "Bạn chỉ hoàn tất được lịch khám do chính bạn phụ trách.", HttpStatus.FORBIDDEN);
        }
        if (!"CHECKED_IN".equals(a.get("status"))) {
            throw new BusinessException("INVALID_APPOINTMENT_STATE", "Chỉ hoàn tất được lịch đang ở trạng thái Đã đến.", HttpStatus.CONFLICT);
        }

        // dịch vụ đã thực hiện: nếu không chỉ định, coi toàn bộ dịch vụ đã đặt là đã thực hiện
        if (req.performedServiceIds() == null) {
            jdbc.update("UPDATE appointment_service SET is_performed = TRUE WHERE appointment_id = ?", appointmentId);
        } else {
            named.update("""
                UPDATE appointment_service SET is_performed = COALESCE(service_id IN (:ids), FALSE)
                 WHERE appointment_id = :appt
                """, new MapSqlParameterSource().addValue("appt", appointmentId)
                    .addValue("ids", req.performedServiceIds().isEmpty() ? List.of(-1L) : req.performedServiceIds()));
        }

        named.update("""
            INSERT INTO treatment_record (appointment_id, patient_id, staff_id, clinical_note, treatment_result, next_appointment_date)
            VALUES (:appt, :patient, :staff, :note, :result, :next)
            ON CONFLICT (appointment_id) DO UPDATE
               SET clinical_note = EXCLUDED.clinical_note, treatment_result = EXCLUDED.treatment_result,
                   next_appointment_date = EXCLUDED.next_appointment_date
            """, new MapSqlParameterSource()
                .addValue("appt", appointmentId).addValue("patient", a.get("patient_id")).addValue("staff", a.get("staff_id"))
                .addValue("note", req.clinicalNote()).addValue("result", req.treatmentResult().trim())
                .addValue("next", req.nextAppointmentDate()));

        jdbc.update("UPDATE appointment SET status = 'COMPLETED' WHERE appointment_id = ?", appointmentId);
        audit.logStaff(actor.getId(), "APPOINTMENT_COMPLETE", "appointment", appointmentId,
                Map.of("status", "CHECKED_IN"), Map.of("status", "COMPLETED"));
        events.publishEvent(new AppointmentCompletedEvent(appointmentId));
    }
}
