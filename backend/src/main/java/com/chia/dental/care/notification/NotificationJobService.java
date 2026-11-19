package com.chia.dental.care.notification;

import com.chia.dental.care.CareConstants;
import com.chia.dental.care.support.CareSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hàng đợi gửi tin tự động (UC-CSKH-03/04/06).
 *  - enqueue(): tạo việc gửi, chống trùng bằng dedupe_key.
 *  - scanReminders(): tạo việc nhắc lịch trước 24h/2h cho lịch Đã xác nhận.
 *  - processDue(): nhận việc đến hạn (FOR UPDATE SKIP LOCKED), kiểm tra điều kiện, gửi Zalo -> SMS dự phòng, ghi log.
 * Việc gọi HTTP nhà cung cấp KHÔNG nằm trong transaction DB.
 */
@Slf4j
@Service
public class NotificationJobService {

    private final NamedParameterJdbcTemplate named;
    private final JdbcTemplate jdbc;
    private final CareSettings config;
    private final OptOutPolicy optOutPolicy;
    private final MessageComposer composer;
    private final Map<String, NotificationSender> senders;

    public NotificationJobService(NamedParameterJdbcTemplate named, JdbcTemplate jdbc, CareSettings config,
                                  OptOutPolicy optOutPolicy, MessageComposer composer,
                                  List<NotificationSender> senderBeans) {
        this.named = named;
        this.jdbc = jdbc;
        this.config = config;
        this.optOutPolicy = optOutPolicy;
        this.composer = composer;
        this.senders = senderBeans.stream().collect(Collectors.toMap(NotificationSender::channel, Function.identity()));
    }

    // ------------------------------------------------------------------ enqueue

    /** @return true nếu tạo mới, false nếu dedupe_key đã tồn tại. */
    public boolean enqueue(String dedupeKey, NotificationType type, long customerId, Long patientId,
                           Long appointmentId, Long careTaskId, OffsetDateTime targetTime,
                           OffsetDateTime runAt) {
        int rows = named.update("""
            INSERT INTO notification_job (dedupe_key, notif_type, customer_id, patient_id, appointment_id,
                                          care_task_id, target_time, scheduled_at, next_attempt_at)
            VALUES (:key, :type, :customerId, :patientId, :appointmentId, :taskId, :target, :runAt, :runAt)
            ON CONFLICT (dedupe_key) DO NOTHING
            """, new MapSqlParameterSource()
                .addValue("key", dedupeKey)
                .addValue("type", type.name())
                .addValue("customerId", customerId)
                .addValue("patientId", patientId)
                .addValue("appointmentId", appointmentId)
                .addValue("taskId", careTaskId)
                .addValue("target", targetTime)
                .addValue("runAt", runAt));
        return rows > 0;
    }

    /** Có việc gửi loại này cho task đang chờ/đang xử lý/đã gửi không (chặn bấm "gửi tin mời" nhiều lần). */
    public boolean hasActiveJob(long careTaskId, NotificationType type) {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM notification_job
             WHERE care_task_id = ? AND notif_type = ? AND status IN ('PENDING','PROCESSING','SENT')
            """, Integer.class, careTaskId, type.name());
        return n != null && n > 0;
    }

    public int countJobs(long careTaskId, NotificationType type) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM notification_job WHERE care_task_id = ? AND notif_type = ?",
                Integer.class, careTaskId, type.name());
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------ nhắc lịch

    /**
     * Tạo việc nhắc lịch cho lịch Đã xác nhận ở các mốc reminder_hours_before (mặc định 24h và 2h).
     * Chỉ tạo khi đang trong "cửa sổ" [mốc, mốc + reminder_grace_minutes] để lịch đặt sát giờ không bị nhắc dồn
     * và để khôi phục được sau khi scheduler tạm ngưng ngắn. dedupe_key chứa giờ hẹn => đổi giờ thì có nhắc mới.
     */
    public int scanReminders() {
        List<Integer> hours = config.getIntList("reminder_hours_before", List.of(24, 2));
        int grace = config.getInt("reminder_grace_minutes", 30);
        String hoursCsv = hours.stream().map(String::valueOf).collect(Collectors.joining(","));
        return named.update("""
            INSERT INTO notification_job (dedupe_key, notif_type, customer_id, patient_id, appointment_id,
                                          target_time, reminder_hours, scheduled_at, next_attempt_at)
            SELECT 'REMINDER:' || a.appointment_id || ':' || h.hrs || ':' || (EXTRACT(EPOCH FROM a.date_time))::BIGINT,
                   'APPOINTMENT_REMINDER', p.customer_id, a.patient_id, a.appointment_id,
                   a.date_time, h.hrs, a.date_time - make_interval(hours => h.hrs), now()
              FROM appointment a
              JOIN patient_profile p ON p.patient_id = a.patient_id
             CROSS JOIN (SELECT unnest(string_to_array(:hours, ','))::int AS hrs) h
             WHERE a.status = 'CONFIRMED'
               AND a.date_time > now()
               AND now() >= a.date_time - make_interval(hours => h.hrs)
               AND now() <  a.date_time - make_interval(hours => h.hrs) + make_interval(mins => :grace)
            ON CONFLICT (dedupe_key) DO NOTHING
            """, new MapSqlParameterSource().addValue("hours", hoursCsv).addValue("grace", grace));
    }

    // ------------------------------------------------------------------ xử lý hàng đợi

    public int processDue(int batchSize) {
        List<Long> ids = jdbc.queryForList("""
            UPDATE notification_job
               SET status = 'PROCESSING', attempts = attempts + 1, locked_until = now() + interval '5 minutes'
             WHERE job_id IN (
                   SELECT job_id FROM notification_job
                    WHERE (status = 'PENDING' AND next_attempt_at <= now())
                       OR (status = 'PROCESSING' AND locked_until < now())
                    ORDER BY next_attempt_at
                    LIMIT ? FOR UPDATE SKIP LOCKED)
            RETURNING job_id
            """, Long.class, batchSize);
        for (Long id : ids) {
            try {
                process(id);
            } catch (Exception e) {
                log.error("Xử lý notification_job {} lỗi", id, e);
                finish(id, "FAILED", null, "INTERNAL_ERROR: " + e.getClass().getSimpleName());
            }
        }
        return ids.size();
    }

    void process(long jobId) {
        JobRow job = loadJob(jobId);
        NotificationType type = NotificationType.valueOf(job.notifType());

        Optional<String> skip = checkStillRelevant(job, type);
        if (skip.isPresent()) {
            finish(jobId, "SKIPPED", skip.get(), null);
            return;
        }
        boolean zaloOk = !optOutPolicy.isBlocked(job.customerId(), type, CareConstants.CH_ZALO);
        boolean smsOk = !optOutPolicy.isBlocked(job.customerId(), type, CareConstants.CH_SMS);
        if (!zaloOk && !smsOk) {
            finish(jobId, "SKIPPED", "OPT_OUT", null);
            return;
        }

        OutboundMessage message = composer.compose(job, type);
        int maxAttempts = config.getInt("notification_max_attempts", 3);
        boolean exhausted = job.attempts() >= maxAttempts;

        SendResult last = null;
        if (zaloOk) {
            SendResult r = send(CareConstants.CH_ZALO, job, message);
            if (r.isSuccess()) { finish(jobId, "SENT", null, null); return; }
            if (r.isUnknown() && !exhausted) { retry(job, r.describe()); return; }
            last = r; // REJECTED (hoặc UNKNOWN đã hết lượt thử) -> chuyển kênh dự phòng theo SRS
        }
        if (smsOk) {
            SendResult r = send(CareConstants.CH_SMS, job, message);
            if (r.isSuccess()) { finish(jobId, "SENT", null, null); return; }
            if (r.isUnknown() && !exhausted) { retry(job, r.describe()); return; }
            last = r;
        }
        finish(jobId, "FAILED", null, last == null ? "NO_CHANNEL" : last.describe());
    }

    /** Điều kiện còn phù hợp để gửi (SRS 3.2.2a: không gửi theo thông tin cũ). */
    private Optional<String> checkStillRelevant(JobRow job, NotificationType type) {
        if (type == NotificationType.APPOINTMENT_REMINDER) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT status, date_time FROM appointment WHERE appointment_id = ?", job.appointmentId());
            if (rows.isEmpty()) return Optional.of("APPOINTMENT_NOT_FOUND");
            if (!"CONFIRMED".equals(rows.get(0).get("status"))) return Optional.of("APPOINTMENT_NOT_CONFIRMED");
            OffsetDateTime start = MessageComposer.toOdt(rows.get(0).get("date_time"));
            if (job.targetTime() != null && !start.isEqual(job.targetTime())) return Optional.of("APPOINTMENT_CHANGED");
            if (!start.isAfter(OffsetDateTime.now())) return Optional.of("APPOINTMENT_PASSED");
        } else if (type == NotificationType.POST_VISIT_CHECK || type == NotificationType.RECALL) {
            List<String> st = jdbc.queryForList("SELECT status FROM care_task WHERE task_id = ?", String.class, job.careTaskId());
            if (st.isEmpty()) return Optional.of("TASK_NOT_FOUND");
            if (CareConstants.TASK_CLOSED.equals(st.get(0))) return Optional.of("TASK_CLOSED");
        }
        return Optional.empty();
    }

    private SendResult send(String channel, JobRow job, OutboundMessage message) {
        NotificationSender sender = senders.get(channel);
        SendResult result = sender == null
                ? SendResult.rejected("SENDER_NOT_AVAILABLE", "Không có nhà cung cấp cho kênh " + channel)
                : sender.send(message);
        logAttempt(job, message, channel, result);
        return result;
    }

    /** Mỗi lần gửi/chuyển kênh = 1 dòng notification_log (FR-28). SUCCESS khi và chỉ khi nhà cung cấp xác nhận. */
    private void logAttempt(JobRow job, OutboundMessage message, String channel, SendResult r) {
        boolean ok = r.isSuccess();
        String error = ok ? null : trunc(r.errorCode() == null ? "UNKNOWN_ERROR" : r.errorCode(), 50);
        named.update("""
            INSERT INTO notification_log (customer_id, appointment_id, care_task_id, notif_type, channel,
                                          recipient_phone, content, status, error_code, provider_msg_id,
                                          sent_at, job_id)
            VALUES (:customerId, :appointmentId, :taskId, :type, :channel, :phone, :content, :status,
                    :error, :providerId, :sentAt, :jobId)
            """, new MapSqlParameterSource()
                .addValue("customerId", job.customerId())
                .addValue("appointmentId", job.appointmentId())
                .addValue("taskId", job.careTaskId())
                .addValue("type", job.notifType())
                .addValue("channel", channel)
                .addValue("phone", job.phone())
                .addValue("content", message.text())
                .addValue("status", ok ? "SUCCESS" : "FAILED")
                .addValue("error", error)
                .addValue("providerId", r.providerMsgId() == null ? null : trunc(r.providerMsgId(), 100))
                .addValue("sentAt", ok ? OffsetDateTime.now() : null)
                .addValue("jobId", job.jobId()));
    }

    private void retry(JobRow job, String error) {
        int minutes = Math.max(1, job.attempts()) * 5;
        jdbc.update("""
            UPDATE notification_job
               SET status = 'PENDING', locked_until = NULL, last_error = ?,
                   next_attempt_at = now() + make_interval(mins => ?)
             WHERE job_id = ?
            """, trunc(error, 255), minutes, job.jobId());
    }

    private void finish(long jobId, String status, String skipReason, String error) {
        jdbc.update("""
            UPDATE notification_job
               SET status = ?, skip_reason = ?, last_error = ?, locked_until = NULL, processed_at = now()
             WHERE job_id = ?
            """, status, skipReason, error == null ? null : trunc(error, 255), jobId);
    }

    private JobRow loadJob(long jobId) {
        return jdbc.queryForObject("""
            SELECT j.job_id, j.notif_type, j.customer_id, j.patient_id, j.appointment_id, j.care_task_id,
                   j.target_time, j.reminder_hours, j.attempts, c.phone
              FROM notification_job j JOIN customer c ON c.customer_id = j.customer_id
             WHERE j.job_id = ?
            """, (rs, i) -> new JobRow(
                rs.getLong("job_id"), rs.getString("notif_type"), rs.getLong("customer_id"),
                (Long) rs.getObject("patient_id"), (Long) rs.getObject("appointment_id"),
                (Long) rs.getObject("care_task_id"),
                rs.getObject("target_time", OffsetDateTime.class),
                (Integer) rs.getObject("reminder_hours"), rs.getInt("attempts"), rs.getString("phone")),
            jobId);
    }

    private static String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
