package com.chia.dental.care;

import com.chia.dental.care.CareDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Truy vấn đọc cho màn hình CSKH (danh sách, tra cứu, dashboard, báo cáo). Không ghi dữ liệu. */
@Repository
@RequiredArgsConstructor
public class CareQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Quá hạn thời gian thực: cờ đã đánh dấu HOẶC đang chờ/cần chuyên môn mà đã qua hạn (scheduler có thể trễ vài phút). */
    private static final String OVERDUE_EXPR =
            "(t.is_overdue OR (t.status IN ('WAITING','NEEDS_MEDICAL') AND t.sla_deadline < now()))";

    // ============================================================ công việc
    private static final String TASK_SELECT = """
        SELECT t.task_id, t.task_type, t.source, t.status, %s AS overdue_now,
               t.scheduled_at, t.visible_from, t.sla_deadline, t.created_at, t.completed_at,
               t.result_note, t.medical_request_note, t.medical_response_note, sc.category AS scenario_category,
               t.appointment_id, a.date_time AS appointment_time, a.staff_id AS doctor_id, d.full_name AS doctor_name,
               t.patient_id, p.full_name AS patient_name, c.customer_id, c.phone,
               EXISTS (SELECT 1 FROM opt_out o WHERE o.customer_id = c.customer_id AND o.is_active) AS opted_out,
               t.assignee_id, s.full_name AS assignee_name
          FROM care_task t
          JOIN patient_profile p ON p.patient_id = t.patient_id
          JOIN customer c ON c.customer_id = p.customer_id
          JOIN staff_account s ON s.staff_id = t.assignee_id
          LEFT JOIN appointment a ON a.appointment_id = t.appointment_id
          LEFT JOIN staff_account d ON d.staff_id = a.staff_id
          LEFT JOIN care_scenario sc ON sc.scenario_id = t.care_scenario_id
        """.formatted(OVERDUE_EXPR);

    private final RowMapper<CareTaskView> taskMapper = (rs, i) -> {
        boolean overdue = rs.getBoolean("overdue_now") && !"CLOSED".equals(rs.getString("status"));
        OffsetDateTime sla = odt(rs, "sla_deadline");
        long minutes = overdue ? Math.max(0, Duration.between(sla, OffsetDateTime.now()).toMinutes()) : 0;
        return new CareTaskView(
                rs.getLong("task_id"), rs.getString("task_type"), rs.getString("source"), rs.getString("status"),
                overdue, minutes,
                odt(rs, "scheduled_at"), odt(rs, "visible_from"), sla, odt(rs, "created_at"), odt(rs, "completed_at"),
                rs.getString("result_note"), rs.getString("medical_request_note"), rs.getString("medical_response_note"),
                rs.getString("scenario_category"),
                lng(rs, "appointment_id"), odt(rs, "appointment_time"), lng(rs, "doctor_id"), rs.getString("doctor_name"),
                rs.getLong("patient_id"), rs.getString("patient_name"), rs.getLong("customer_id"), rs.getString("phone"),
                rs.getBoolean("opted_out"),
                rs.getLong("assignee_id"), rs.getString("assignee_name"));
    };

    /**
     * @param doctorId   khác null => chỉ việc thuộc lịch hẹn của bác sĩ này (phạm vi bác sĩ)
     * @param dueOnly    true => chỉ việc đã "đến hạn xuất hiện" (visible_from <= now)
     */
    public PageResult<CareTaskView> listTasks(String status, String type, Boolean overdue, Long assigneeId,
                                              Long doctorId, boolean dueOnly, int page, int size) {
        String where = """
            WHERE (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR))
              AND (CAST(:type AS VARCHAR) IS NULL OR t.task_type = CAST(:type AS VARCHAR))
              AND (CAST(:assigneeId AS BIGINT) IS NULL OR t.assignee_id = CAST(:assigneeId AS BIGINT))
              AND (CAST(:doctorId AS BIGINT) IS NULL OR a.staff_id = CAST(:doctorId AS BIGINT))
              AND (:dueOnly = FALSE OR t.visible_from <= now())
              AND (CAST(:overdue AS BOOLEAN) IS NULL
                   OR (CAST(:overdue AS BOOLEAN) = TRUE AND t.status <> 'CLOSED' AND %s)
                   OR (CAST(:overdue AS BOOLEAN) = FALSE AND NOT %s))
            """.formatted(OVERDUE_EXPR, OVERDUE_EXPR);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("status", status).addValue("type", type).addValue("overdue", overdue)
                .addValue("assigneeId", assigneeId).addValue("doctorId", doctorId)
                .addValue("dueOnly", dueOnly)
                .addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM care_task t
              JOIN patient_profile p ON p.patient_id = t.patient_id
              LEFT JOIN appointment a ON a.appointment_id = t.appointment_id
            """ + where, p, Long.class);
        List<CareTaskView> items = jdbc.query(TASK_SELECT + where
                + " ORDER BY overdue_now DESC, t.sla_deadline ASC, t.task_id LIMIT :limit OFFSET :offset", p, taskMapper);
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    public Optional<CareTaskView> findTask(long taskId) {
        List<CareTaskView> rows = jdbc.query(TASK_SELECT + " WHERE t.task_id = :id",
                new MapSqlParameterSource("id", taskId), taskMapper);
        return rows.stream().findFirst();
    }

    /** Việc "Cần hỗ trợ chuyên môn" thuộc bệnh nhân của bác sĩ (banner ở B2). */
    public List<CareTaskView> medicalSupportTasks(long doctorId) {
        return jdbc.query(TASK_SELECT + """
             WHERE t.status = 'NEEDS_MEDICAL' AND a.staff_id = :doctorId
             ORDER BY t.sla_deadline
            """, new MapSqlParameterSource("doctorId", doctorId), taskMapper);
    }

    // ============================================================ lịch sắp đến (FR-23)
    public List<UpcomingAppointmentView> upcomingAppointments(int hours) {
        int thisYear = Year.now(CareConstants.ZONE).getValue();
        return jdbc.query("""
            SELECT a.appointment_id, a.date_time, a.status, a.note,
                   p.patient_id, p.full_name, p.birth_year, c.customer_id, c.phone, d.full_name AS doctor_name,
                   COALESCE((SELECT string_agg(s.name, ', ' ORDER BY s.name)
                               FROM appointment_service aps JOIN service s ON s.service_id = aps.service_id
                              WHERE aps.appointment_id = a.appointment_id), '') AS services,
                   EXISTS (SELECT 1 FROM appointment_service aps
                             JOIN service s ON s.service_id = aps.service_id
                             JOIN care_scenario sc ON sc.scenario_id = s.care_scenario_id
                            WHERE aps.appointment_id = a.appointment_id AND sc.category = 'MINOR_SURGERY') AS has_minor_surgery,
                   EXISTS (SELECT 1 FROM care_task t WHERE t.appointment_id = a.appointment_id
                              AND t.task_type = 'CONFIRM_CALL' AND t.status = 'CLOSED') AS confirmed_by_call,
                   EXISTS (SELECT 1 FROM opt_out o WHERE o.customer_id = c.customer_id AND o.is_active) AS opted_out
              FROM appointment a
              JOIN patient_profile p ON p.patient_id = a.patient_id
              JOIN customer c ON c.customer_id = p.customer_id
              JOIN staff_account d ON d.staff_id = a.staff_id
             WHERE a.status = 'CONFIRMED'
               AND a.date_time BETWEEN now() AND now() + make_interval(hours => :hours)
             ORDER BY a.date_time
            """, new MapSqlParameterSource("hours", hours), (rs, i) -> new UpcomingAppointmentView(
                rs.getLong("appointment_id"), odt(rs, "date_time"), rs.getString("status"), rs.getString("note"),
                rs.getLong("patient_id"), rs.getString("full_name"), thisYear - rs.getInt("birth_year"),
                rs.getLong("customer_id"), rs.getString("phone"), rs.getString("doctor_name"),
                rs.getString("services"), rs.getBoolean("has_minor_surgery"),
                rs.getBoolean("confirmed_by_call"), rs.getBoolean("opted_out")));
    }

    // ============================================================ khách hàng
    public List<CustomerSummary> searchCustomers(String q) {
        String like = q == null || q.isBlank() ? null
                : "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("""
            SELECT c.customer_id, c.phone, c.is_blacklisted,
                   COALESCE(string_agg(p.full_name, ', ' ORDER BY p.patient_id), '') AS patient_names
              FROM customer c LEFT JOIN patient_profile p ON p.customer_id = c.customer_id
             WHERE CAST(:like AS VARCHAR) IS NULL
                OR lower(c.phone) LIKE CAST(:like AS VARCHAR) ESCAPE '\\'
                OR lower(p.full_name) LIKE CAST(:like AS VARCHAR) ESCAPE '\\'
             GROUP BY c.customer_id
             ORDER BY c.customer_id DESC
             LIMIT 50
            """, new MapSqlParameterSource("like", like), (rs, i) -> new CustomerSummary(
                rs.getLong("customer_id"), rs.getString("phone"), rs.getBoolean("is_blacklisted"),
                rs.getString("patient_names")));
    }

    public Optional<CustomerCareOverview> customerOverview(long customerId) {
        MapSqlParameterSource p = new MapSqlParameterSource("cid", customerId);
        List<Map<String, Object>> base = jdbc.queryForList(
                "SELECT phone, is_blacklisted, created_at FROM customer WHERE customer_id = :cid", p);
        if (base.isEmpty()) return Optional.empty();

        List<PatientBrief> patients = jdbc.query("""
            SELECT patient_id, full_name, birth_year, gender FROM patient_profile
             WHERE customer_id = :cid ORDER BY patient_id
            """, p, (rs, i) -> new PatientBrief(rs.getLong(1), rs.getString(2), rs.getShort(3), rs.getString(4)));

        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("""
            SELECT a.status, count(*) FROM appointment a JOIN patient_profile pp ON pp.patient_id = a.patient_id
             WHERE pp.customer_id = :cid GROUP BY a.status
            """, p, rs -> { counts.put(rs.getString(1), rs.getLong(2)); });

        Long noShow = jdbc.queryForObject("""
            SELECT count(*) FROM no_show_event e JOIN patient_profile pp ON pp.patient_id = e.patient_id
             WHERE pp.customer_id = :cid AND NOT e.is_reversed AND e.occurred_at >= now() - interval '12 months'
            """, p, Long.class);

        List<OptOutView> optOuts = jdbc.query("""
            SELECT opt_out_id, channel, excluded_notif_types::text, reason, created_at, created_by
              FROM opt_out WHERE customer_id = :cid AND is_active ORDER BY opt_out_id
            """, p, (rs, i) -> new OptOutView(rs.getLong(1), rs.getString(2), parseJsonArray(rs.getString(3)),
                rs.getString(4), odt(rs, "created_at"), rs.getLong(6)));

        List<VisitBrief> visits = jdbc.query("""
            SELECT a.appointment_id, a.date_time, d.full_name AS doctor_name, pp.full_name AS patient_name, a.status,
                   COALESCE((SELECT string_agg(s.name, ', ' ORDER BY s.name) FROM appointment_service aps
                              JOIN service s ON s.service_id = aps.service_id
                             WHERE aps.appointment_id = a.appointment_id), '') AS services
              FROM appointment a
              JOIN patient_profile pp ON pp.patient_id = a.patient_id
              JOIN staff_account d ON d.staff_id = a.staff_id
             WHERE pp.customer_id = :cid ORDER BY a.date_time DESC LIMIT 10
            """, p, (rs, i) -> new VisitBrief(rs.getLong("appointment_id"), odt(rs, "date_time"),
                rs.getString("doctor_name"), rs.getString("patient_name"), rs.getString("services"), rs.getString("status")));

        List<CareTaskView> tasks = jdbc.query(TASK_SELECT + " WHERE c.customer_id = :cid ORDER BY t.created_at DESC LIMIT 10",
                p, taskMapper);
        List<NotificationLogView> notifs = notificationLogs(customerId, null, null, null, null, 0, 20).items();
        List<FeedbackView> feedback = listFeedback(null, false, true, customerId, 0, 10).items();

        Map<String, Object> b = base.get(0);
        return Optional.of(new CustomerCareOverview(customerId, (String) b.get("phone"),
                Boolean.TRUE.equals(b.get("is_blacklisted")), MessageTime.odt(b.get("created_at")),
                patients, counts, noShow == null ? 0 : noShow, optOuts, visits, tasks, notifs, feedback));
    }

    // ============================================================ nhật ký gửi tin (FR-28)
    public PageResult<NotificationLogView> notificationLogs(Long customerId, String status, String type,
                                                            OffsetDateTime from, OffsetDateTime to, int page, int size) {
        String where = """
            WHERE (CAST(:customerId AS BIGINT) IS NULL OR l.customer_id = CAST(:customerId AS BIGINT))
              AND (CAST(:status AS VARCHAR) IS NULL OR l.status = CAST(:status AS VARCHAR))
              AND (CAST(:type AS VARCHAR) IS NULL OR l.notif_type = CAST(:type AS VARCHAR))
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR l.created_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR l.created_at < CAST(:to AS TIMESTAMPTZ))
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("customerId", customerId).addValue("status", status).addValue("type", type)
                .addValue("from", from).addValue("to", to)
                .addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM notification_log l " + where, p, Long.class);
        List<NotificationLogView> items = jdbc.query("""
            SELECT l.notif_id, l.customer_id, l.appointment_id, l.care_task_id, l.notif_type, l.channel,
                   l.recipient_phone, l.content, l.status, l.error_code, l.provider_msg_id, l.created_at, l.sent_at
              FROM notification_log l
            """ + where + " ORDER BY l.created_at DESC, l.notif_id DESC LIMIT :limit OFFSET :offset", p,
                (rs, i) -> new NotificationLogView(rs.getLong(1), lng(rs, "customer_id"), lng(rs, "appointment_id"),
                        lng(rs, "care_task_id"), rs.getString("notif_type"), rs.getString("channel"),
                        rs.getString("recipient_phone"), rs.getString("content"), rs.getString("status"),
                        rs.getString("error_code"), rs.getString("provider_msg_id"),
                        odt(rs, "created_at"), odt(rs, "sent_at")));
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    // ============================================================ phản hồi (FR-30..33)
    private static final String FEEDBACK_SELECT = """
        SELECT f.feedback_id, f.appointment_id, f.patient_id, p.full_name AS patient_name, f.customer_id,
               f.staff_id AS doctor_id, d.full_name AS doctor_name, f.rating_score, f.comment,
               f.alert_flag, f.is_hidden, f.created_at, cp.complaint_id, cp.status AS complaint_status
          FROM feedback f
          JOIN patient_profile p ON p.patient_id = f.patient_id
          JOIN staff_account d ON d.staff_id = f.staff_id
          LEFT JOIN complaint cp ON cp.feedback_id = f.feedback_id
        """;

    private static final RowMapper<FeedbackView> FEEDBACK_MAPPER = (rs, i) -> new FeedbackView(
            rs.getLong("feedback_id"), rs.getLong("appointment_id"), rs.getLong("patient_id"), rs.getString("patient_name"),
            rs.getLong("customer_id"), rs.getLong("doctor_id"), rs.getString("doctor_name"), rs.getShort("rating_score"),
            rs.getString("comment"), rs.getBoolean("alert_flag"), rs.getBoolean("is_hidden"), odt(rs, "created_at"),
            lng(rs, "complaint_id"), rs.getString("complaint_status"));

    /**
     * @param doctorId      khác null => chỉ phản hồi của bệnh nhân bác sĩ này trực tiếp khám (FR-33)
     * @param includeHidden chỉ Quản lý mới được xem phản hồi đã ẩn
     */
    public PageResult<FeedbackView> listFeedback(Long doctorId, boolean alertOnly, boolean includeHidden,
                                                 Long customerId, int page, int size) {
        String where = """
            WHERE (CAST(:doctorId AS BIGINT) IS NULL OR f.staff_id = CAST(:doctorId AS BIGINT))
              AND (:alertOnly = FALSE OR f.alert_flag)
              AND (:includeHidden OR NOT f.is_hidden)
              AND (CAST(:customerId AS BIGINT) IS NULL OR f.customer_id = CAST(:customerId AS BIGINT))
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("doctorId", doctorId).addValue("alertOnly", alertOnly)
                .addValue("includeHidden", includeHidden).addValue("customerId", customerId)
                .addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM feedback f " + where, p, Long.class);
        List<FeedbackView> items = jdbc.query(FEEDBACK_SELECT + where
                + " ORDER BY f.alert_flag DESC, f.created_at DESC LIMIT :limit OFFSET :offset", p, FEEDBACK_MAPPER);
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    // ============================================================ khiếu nại (FR-32)
    private static final String COMPLAINT_SELECT = """
        SELECT cp.complaint_id, cp.patient_id, p.full_name AS patient_name, c.phone, cp.feedback_id, f.rating_score,
               cp.content, cp.severity, cp.handler_id, h.full_name AS handler_name, cp.action_taken, cp.result,
               cp.status, cp.sla_deadline,
               (cp.status <> 'CLOSED' AND cp.sla_deadline IS NOT NULL AND cp.sla_deadline < now()) AS overdue,
               cp.is_hidden, cp.created_at, cp.closed_at
          FROM complaint cp
          JOIN patient_profile p ON p.patient_id = cp.patient_id
          JOIN customer c ON c.customer_id = p.customer_id
          LEFT JOIN feedback f ON f.feedback_id = cp.feedback_id
          LEFT JOIN staff_account h ON h.staff_id = cp.handler_id
        """;

    private static final RowMapper<ComplaintView> COMPLAINT_MAPPER = (rs, i) -> new ComplaintView(
            rs.getLong("complaint_id"), rs.getLong("patient_id"), rs.getString("patient_name"), rs.getString("phone"),
            lng(rs, "feedback_id"), shortOrNull(rs, "rating_score"), rs.getString("content"), rs.getString("severity"),
            lng(rs, "handler_id"), rs.getString("handler_name"), rs.getString("action_taken"), rs.getString("result"),
            rs.getString("status"), odt(rs, "sla_deadline"), rs.getBoolean("overdue"), rs.getBoolean("is_hidden"),
            odt(rs, "created_at"), odt(rs, "closed_at"));

    public PageResult<ComplaintView> listComplaints(String status, boolean includeHidden, Long doctorId, int page, int size) {
        String where = """
            WHERE (CAST(:status AS VARCHAR) IS NULL OR cp.status = CAST(:status AS VARCHAR))
              AND (:includeHidden OR NOT cp.is_hidden)
              AND (CAST(:doctorId AS BIGINT) IS NULL OR f.staff_id = CAST(:doctorId AS BIGINT))
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("status", status).addValue("includeHidden", includeHidden).addValue("doctorId", doctorId)
                .addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM complaint cp LEFT JOIN feedback f ON f.feedback_id = cp.feedback_id
            """ + where, p, Long.class);
        List<ComplaintView> items = jdbc.query(COMPLAINT_SELECT + where + """
             ORDER BY (cp.status = 'CLOSED'), CASE cp.severity WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END,
                      cp.sla_deadline NULLS LAST, cp.created_at DESC
             LIMIT :limit OFFSET :offset
            """, p, COMPLAINT_MAPPER);
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    public Optional<ComplaintView> findComplaint(long id) {
        return jdbc.query(COMPLAINT_SELECT + " WHERE cp.complaint_id = :id",
                new MapSqlParameterSource("id", id), COMPLAINT_MAPPER).stream().findFirst();
    }

    /** Bác sĩ có được xem khiếu nại này không (chỉ khi gắn với phản hồi của bệnh nhân mình khám). */
    public boolean complaintBelongsToDoctor(long complaintId, long doctorId) {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM complaint cp JOIN feedback f ON f.feedback_id = cp.feedback_id
             WHERE cp.complaint_id = :id AND f.staff_id = :doctorId
            """, new MapSqlParameterSource().addValue("id", complaintId).addValue("doctorId", doctorId), Integer.class);
        return n != null && n > 0;
    }

    // ============================================================ dashboard & báo cáo
    public DashboardView dashboard() {
        MapSqlParameterSource none = new MapSqlParameterSource();
        long waiting = count("SELECT count(*) FROM care_task t WHERE t.status = 'WAITING' AND t.visible_from <= now()", none);
        long inProgress = count("SELECT count(*) FROM care_task t WHERE t.status = 'IN_PROGRESS'", none);
        long needsMedical = count("SELECT count(*) FROM care_task t WHERE t.status = 'NEEDS_MEDICAL'", none);
        long overdue = count("SELECT count(*) FROM care_task t WHERE t.status <> 'CLOSED' AND " + OVERDUE_EXPR, none);
        long openComplaints = count("SELECT count(*) FROM complaint WHERE status <> 'CLOSED' AND NOT is_hidden", none);
        long overdueComplaints = count("""
            SELECT count(*) FROM complaint WHERE status <> 'CLOSED' AND NOT is_hidden
               AND sla_deadline IS NOT NULL AND sla_deadline < now()""", none);
        long alertFeedbacks = count("""
            SELECT count(*) FROM feedback f LEFT JOIN complaint cp ON cp.feedback_id = f.feedback_id
             WHERE f.alert_flag AND NOT f.is_hidden AND (cp.complaint_id IS NULL OR cp.status <> 'CLOSED')""", none);
        long failed24h = count("""
            SELECT count(*) FROM notification_job WHERE status = 'FAILED' AND processed_at >= now() - interval '24 hours'""", none);

        List<FeedbackView> redAlerts = jdbc.query(FEEDBACK_SELECT + """
             WHERE f.alert_flag AND NOT f.is_hidden AND (cp.complaint_id IS NULL OR cp.status <> 'CLOSED')
             ORDER BY f.created_at DESC LIMIT 10""", none, FEEDBACK_MAPPER);
        List<FeedbackView> latest = jdbc.query(FEEDBACK_SELECT + " WHERE NOT f.is_hidden ORDER BY f.created_at DESC LIMIT 10",
                none, FEEDBACK_MAPPER);
        return new DashboardView(waiting, inProgress, needsMedical, overdue, openComplaints, overdueComplaints,
                alertFeedbacks, failed24h, redAlerts, latest);
    }

    public CareReportSummary report(OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("from", from).addValue("to", to);
        String range = " (CAST(:from AS TIMESTAMPTZ) IS NULL OR %1$s >= CAST(:from AS TIMESTAMPTZ))"
                + " AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR %1$s < CAST(:to AS TIMESTAMPTZ)) ";

        Map<String, Long> byStatus = group("SELECT t.status, count(*) FROM care_task t WHERE "
                + range.formatted("t.created_at") + " GROUP BY t.status", p);
        Map<String, Long> byType = group("SELECT t.task_type, count(*) FROM care_task t WHERE "
                + range.formatted("t.created_at") + " GROUP BY t.task_type", p);
        long overdueOpen = count("SELECT count(*) FROM care_task t WHERE t.status <> 'CLOSED' AND " + OVERDUE_EXPR
                + " AND " + range.formatted("t.created_at"), p);
        Map<String, Long> notifs = group("SELECT l.channel || ':' || l.status, count(*) FROM notification_log l WHERE "
                + range.formatted("l.created_at") + " GROUP BY l.channel, l.status", p);
        long okCount = notifs.entrySet().stream().filter(e -> e.getKey().endsWith(":SUCCESS")).mapToLong(Map.Entry::getValue).sum();
        long allCount = notifs.values().stream().mapToLong(Long::longValue).sum();
        Double successRate = allCount == 0 ? null : (double) okCount / allCount;

        Map<String, Object> fb = jdbc.queryForMap("""
            SELECT count(*) AS n, avg(f.rating_score)::float8 AS avg_rating, count(*) FILTER (WHERE f.alert_flag) AS alerts
              FROM feedback f WHERE """ + range.formatted("f.created_at"), p);
        Map<String, Long> complaints = group("SELECT cp.status, count(*) FROM complaint cp WHERE "
                + range.formatted("cp.created_at") + " GROUP BY cp.status", p);
        List<CareTaskView> overdueTasks = jdbc.query(TASK_SELECT + " WHERE t.status <> 'CLOSED' AND " + OVERDUE_EXPR
                + " ORDER BY t.sla_deadline LIMIT 100", new MapSqlParameterSource(), taskMapper);

        return new CareReportSummary(from, to, byStatus, byType, overdueOpen, notifs, successRate,
                ((Number) fb.get("n")).longValue(),
                fb.get("avg_rating") == null ? null : ((Number) fb.get("avg_rating")).doubleValue(),
                ((Number) fb.get("alerts")).longValue(), complaints, overdueTasks);
    }

    // ============================================================ helper
    private long count(String sql, MapSqlParameterSource p) {
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n == null ? 0 : n;
    }

    private Map<String, Long> group(String sql, MapSqlParameterSource p) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query(sql, p, rs -> { out.put(rs.getString(1), rs.getLong(2)); });
        return out;
    }

    private static List<String> parseJsonArray(String json) {
        List<String> out = new ArrayList<>();
        if (json == null) return out;
        for (String part : json.replaceAll("[\\[\\]\"]", "").split(",")) {
            if (!part.isBlank()) out.add(part.trim());
        }
        return out;
    }

    static OffsetDateTime odt(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, OffsetDateTime.class);
    }

    static Short shortOrNull(ResultSet rs, String col) throws SQLException {
        short v = rs.getShort(col);
        return rs.wasNull() ? null : v;
    }

    static Long lng(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    /** queryForList trả Timestamp -> chuẩn hóa. */
    private static final class MessageTime {
        static OffsetDateTime odt(Object v) {
            if (v instanceof OffsetDateTime o) return o;
            if (v instanceof java.sql.Timestamp ts) return ts.toInstant().atZone(CareConstants.ZONE).toOffsetDateTime();
            return null;
        }
    }
}
