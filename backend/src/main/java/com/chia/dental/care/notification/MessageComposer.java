package com.chia.dental.care.notification;

import com.chia.dental.care.CareConstants;
import com.chia.dental.care.CareProperties;
import com.chia.dental.care.feedback.FeedbackTokenService;
import com.chia.dental.care.support.CareSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Soạn nội dung tin theo loại thông báo (SRS 2.2.3: tên phòng khám, thời gian, bác sĩ, dịch vụ, lưu ý đặc biệt).
 * Tên tham số trong {@code params} phải khớp tham số của template ZNS đã đăng ký — chỉnh tại đây nếu template khác.
 */
@Component
@RequiredArgsConstructor
public class MessageComposer {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NamedParameterJdbcTemplate jdbc;
    private final CareSettings config;
    private final CareProperties careProperties;
    private final FeedbackTokenService tokenService;

    public OutboundMessage compose(JobRow job, NotificationType type) {
        String clinic = config.getString("clinic_name", "Phòng khám nha khoa CHIA");
        String hotline = config.getString("clinic_hotline", "1900 1234");
        return switch (type) {
            case APPOINTMENT_REMINDER -> reminder(job, type, clinic, hotline);
            case POST_VISIT_CHECK -> postVisit(job, type, clinic, hotline);
            case RECALL -> recall(job, type, clinic, hotline);
            default -> throw new IllegalArgumentException("Loại tin chưa hỗ trợ trong hàng đợi CSKH: " + type);
        };
    }

    private OutboundMessage reminder(JobRow job, NotificationType type, String clinic, String hotline) {
        Map<String, Object> a = jdbc.queryForMap("""
            SELECT a.date_time, d.full_name AS doctor_name, p.full_name AS patient_name,
                   COALESCE((SELECT string_agg(s.name, ', ' ORDER BY s.name)
                               FROM appointment_service aps JOIN service s ON s.service_id = aps.service_id
                              WHERE aps.appointment_id = a.appointment_id), 'Khám theo lịch hẹn') AS services,
                   (SELECT string_agg(DISTINCT s.pre_visit_instruction, ' ')
                      FROM appointment_service aps JOIN service s ON s.service_id = aps.service_id
                     WHERE aps.appointment_id = a.appointment_id AND s.pre_visit_instruction IS NOT NULL) AS note
              FROM appointment a
              JOIN staff_account d ON d.staff_id = a.staff_id
              JOIN patient_profile p ON p.patient_id = a.patient_id
             WHERE a.appointment_id = :id
            """, new MapSqlParameterSource("id", job.appointmentId()));

        OffsetDateTime time = toOdt(a.get("date_time"));
        String when = time.atZoneSameInstant(CareConstants.ZONE).format(TIME_FMT);
        String doctor = String.valueOf(a.get("doctor_name"));
        String services = String.valueOf(a.get("services"));
        String note = a.get("note") == null ? "" : String.valueOf(a.get("note"));

        StringBuilder text = new StringBuilder()
                .append('[').append(clinic).append("] Nhắc lịch hẹn: ").append(when)
                .append(", ").append(doctor).append(", dịch vụ: ").append(services).append('.');
        if (!note.isBlank()) text.append(" Lưu ý: ").append(note);
        text.append(" Cần hỗ trợ vui lòng gọi ").append(hotline).append('.');

        Map<String, String> params = new LinkedHashMap<>();
        params.put("clinic_name", clinic);
        params.put("patient_name", String.valueOf(a.get("patient_name")));
        params.put("appointment_time", when);
        params.put("doctor_name", doctor);
        params.put("service_names", services);
        params.put("note", note.isBlank() ? "Không có" : note);
        params.put("hotline", hotline);
        return new OutboundMessage(type, job.phone(), text.toString(), params, "job-" + job.jobId());
    }

    private OutboundMessage postVisit(JobRow job, NotificationType type, String clinic, String hotline) {
        Map<String, Object> row = jdbc.queryForMap("""
            SELECT p.full_name AS patient_name, COALESCE(sc.message_template, '') AS template
              FROM care_task t
              JOIN patient_profile p ON p.patient_id = t.patient_id
              LEFT JOIN care_scenario sc ON sc.scenario_id = t.care_scenario_id
             WHERE t.task_id = :id
            """, new MapSqlParameterSource("id", job.careTaskId()));

        String template = String.valueOf(row.get("template"));
        String link = "";
        if (job.appointmentId() != null) {
            link = tokenService.issue(job.appointmentId(), OffsetDateTime.now(CareConstants.ZONE))
                    .map(t -> careProperties.getFeedbackBaseUrl() + "/" + t).orElse("");
        }

        StringBuilder text = new StringBuilder().append('[').append(clinic).append("] ");
        text.append(template.isBlank() ? "Phòng khám hỏi thăm tình trạng sức khỏe của quý khách sau điều trị." : template);
        if (!link.isBlank()) text.append(" Mời quý khách đánh giá dịch vụ: ").append(link);
        text.append(" Nếu đau nhiều hoặc chảy máu, gọi ngay ").append(hotline).append('.');

        Map<String, String> params = new LinkedHashMap<>();
        params.put("clinic_name", clinic);
        params.put("patient_name", String.valueOf(row.get("patient_name")));
        params.put("message", template);
        params.put("feedback_link", link);
        params.put("hotline", hotline);
        return new OutboundMessage(type, job.phone(), text.toString(), params, "job-" + job.jobId());
    }

    private OutboundMessage recall(JobRow job, NotificationType type, String clinic, String hotline) {
        Map<String, Object> row = jdbc.queryForMap("""
            SELECT p.full_name AS patient_name, t.scheduled_at
              FROM care_task t JOIN patient_profile p ON p.patient_id = t.patient_id
             WHERE t.task_id = :id
            """, new MapSqlParameterSource("id", job.careTaskId()));
        String date = toOdt(row.get("scheduled_at")).atZoneSameInstant(CareConstants.ZONE).format(DATE_FMT);
        String patient = String.valueOf(row.get("patient_name"));

        String text = "[" + clinic + "] Đã đến lúc tái khám cho " + patient + " (dự kiến " + date
                + "). Vui lòng liên hệ " + hotline + " để đặt lịch hẹn.";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("clinic_name", clinic);
        params.put("patient_name", patient);
        params.put("recall_date", date);
        params.put("hotline", hotline);
        return new OutboundMessage(type, job.phone(), text, params, "job-" + job.jobId());
    }

    /** queryForMap trả java.sql.Timestamp/OffsetDateTime tùy driver -> chuẩn hóa. */
    static OffsetDateTime toOdt(Object value) {
        if (value instanceof OffsetDateTime odt) return odt;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant().atZone(CareConstants.ZONE).toOffsetDateTime();
        throw new IllegalStateException("Kiểu thời gian không hỗ trợ: " + (value == null ? "null" : value.getClass()));
    }
}
