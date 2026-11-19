package com.chia.dental.care.task;

import com.chia.dental.care.CareConstants;
import com.chia.dental.care.notification.NotificationJobService;
import com.chia.dental.care.notification.NotificationType;
import com.chia.dental.care.support.AssigneeSelector;
import com.chia.dental.care.support.CareSettings;
import com.chia.dental.care.support.WorkingTimeCalculator;
import com.chia.dental.common.audit.AuditLogService;
import com.chia.dental.entity.CareTask;
import com.chia.dental.repository.CareTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tự sinh việc CSKH khi lịch hẹn chuyển "Đã hoàn tất" (UC-CSKH-02/04/05, FR-24/25).
 *  - POST_VISIT_CHECK: hỏi thăm sau khám theo kịch bản của dịch vụ (Care_Scenario), kèm tin tự động + link đánh giá.
 *  - RECALL: nhắc tái khám theo ngày bác sĩ đề xuất (treatment_record.next_appointment_date) hoặc chu kỳ dịch vụ.
 * Idempotent: unique index (appointment_id, task_type) cho việc SYSTEM + dedupe_key của job.
 * Dịch vụ không có kịch bản/chu kỳ -> không phát sinh việc ("Không phát sinh công việc CSKH").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareTriggerService {

    /** Thứ tự ưu tiên khi lịch có nhiều dịch vụ: lấy kịch bản cần theo dõi sát nhất. */
    private static final List<String> CATEGORY_PRIORITY = List.of("MINOR_SURGERY", "PROSTHETIC", "LONG_TERM", "GENERAL");

    private final JdbcTemplate jdbc;
    private final CareTaskRepository taskRepository;
    private final AssigneeSelector assigneeSelector;
    private final WorkingTimeCalculator workingTime;
    private final CareSettings config;
    private final NotificationJobService jobService;
    private final AuditLogService audit;

    /** @return số việc mới tạo. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int onAppointmentCompleted(long appointmentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT a.status, a.patient_id, p.customer_id
              FROM appointment a JOIN patient_profile p ON p.patient_id = a.patient_id
             WHERE a.appointment_id = ?
            """, appointmentId);
        if (rows.isEmpty() || !"COMPLETED".equals(rows.get(0).get("status"))) return 0;
        long patientId = ((Number) rows.get(0).get("patient_id")).longValue();
        long customerId = ((Number) rows.get(0).get("customer_id")).longValue();

        OffsetDateTime now = OffsetDateTime.now(CareConstants.ZONE);
        int slaHours = config.getInt("care_sla_hours", 48);
        int created = 0;

        // ---- hỏi thăm sau khám
        Optional<Scenario> scenario = resolveScenario(appointmentId);
        if (scenario.isPresent()
                && !taskRepository.existsByAppointmentIdAndTaskTypeAndSource(appointmentId, CareConstants.TYPE_POST_VISIT, CareConstants.SOURCE_SYSTEM)) {
            Scenario sc = scenario.get();
            OffsetDateTime scheduled = workingTime.rollIntoWorkingHours(now.plusDays(sc.minDays()));
            OffsetDateTime sla = workingTime.addWorkingHours(scheduled, slaHours);
            CareTask task = save(appointmentId, patientId, CareConstants.TYPE_POST_VISIT, sc.scenarioId(), now, scheduled, scheduled, sla);
            jobService.enqueue("POST_VISIT:" + appointmentId, NotificationType.POST_VISIT_CHECK, customerId, patientId,
                    appointmentId, task.getTaskId(), null, scheduled);
            created++;
        }

        // ---- nhắc tái khám
        Optional<OffsetDateTime> recallAt = resolveRecallDate(appointmentId, now);
        if (recallAt.isPresent()
                && !taskRepository.existsByAppointmentIdAndTaskTypeAndSource(appointmentId, CareConstants.TYPE_RECALL, CareConstants.SOURCE_SYSTEM)) {
            OffsetDateTime due = recallAt.get();
            OffsetDateTime visible = due.minusDays(config.getInt("recall_lead_days", 7));
            if (visible.isBefore(now)) visible = now;
            OffsetDateTime sla = workingTime.addWorkingHours(visible, slaHours);
            Long scenarioId = scenario.map(Scenario::scenarioId).orElse(null);
            save(appointmentId, patientId, CareConstants.TYPE_RECALL, scenarioId, now, due, visible, sla);
            created++;
        }

        if (created == 0) {
            log.info("Lịch {}: không phát sinh công việc CSKH", appointmentId);
        } else {
            audit.logSystem("CARE_TASK_AUTO_CREATE", "appointment", appointmentId, null, Map.of("createdTasks", created));
        }
        return created;
    }

    /**
     * Đối soát: lịch Đã hoàn tất gần đây chưa có việc SYSTEM (sự kiện bị lỡ do lỗi/khởi động lại, hoặc phân hệ khác hoàn tất lịch).
     * Chỉ quét lịch hoàn tất trong {@code lookbackDays} ngày; idempotent nên an toàn khi chạy lặp.
     */
    public int reconcile(int lookbackDays) {
        List<Long> ids = jdbc.queryForList("""
            SELECT a.appointment_id FROM appointment a
             WHERE a.status = 'COMPLETED' AND a.date_time >= now() - make_interval(days => ?)
               AND NOT EXISTS (SELECT 1 FROM care_task t WHERE t.appointment_id = a.appointment_id AND t.source = 'SYSTEM')
            """, Long.class, lookbackDays);
        int total = 0;
        for (Long id : ids) {
            try {
                total += onAppointmentCompleted(id);
            } catch (Exception e) {
                log.warn("Đối soát CSKH lịch {} lỗi: {}", id, e.getMessage());
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ nội bộ

    private CareTask save(long appointmentId, long patientId, String type, Long scenarioId, OffsetDateTime now,
                          OffsetDateTime scheduled, OffsetDateTime visible, OffsetDateTime sla) {
        return taskRepository.save(CareTask.builder()
                .appointmentId(appointmentId).patientId(patientId).taskType(type).source(CareConstants.SOURCE_SYSTEM)
                .careScenarioId(scenarioId).assigneeId(assigneeSelector.nextForTask())
                .createdAt(now).scheduledAt(scheduled).visibleFrom(visible).slaDeadline(sla)
                .status(CareConstants.TASK_WAITING).isOverdue(false).build());
    }

    private record Scenario(long scenarioId, String category, int minDays) {}

    /** Kịch bản ưu tiên cao nhất trong các dịch vụ đã thực hiện (nếu chưa đánh dấu thì lấy toàn bộ dịch vụ của lịch). */
    private Optional<Scenario> resolveScenario(long appointmentId) {
        List<Scenario> all = jdbc.query("""
            SELECT DISTINCT sc.scenario_id, sc.category, sc.followup_min_days
              FROM appointment_service aps
              JOIN service s ON s.service_id = aps.service_id
              JOIN care_scenario sc ON sc.scenario_id = s.care_scenario_id
             WHERE aps.appointment_id = ?
               AND (aps.is_performed OR NOT EXISTS (SELECT 1 FROM appointment_service x
                                                     WHERE x.appointment_id = aps.appointment_id AND x.is_performed))
            """, (rs, i) -> new Scenario(rs.getLong(1), rs.getString(2), rs.getInt(3)), appointmentId);
        return all.stream().min((a, b) -> Integer.compare(rank(a.category()), rank(b.category())));
    }

    private static int rank(String category) {
        int i = CATEGORY_PRIORITY.indexOf(category);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** Ngày bác sĩ đề xuất (ưu tiên) hoặc ngày hoàn tất + chu kỳ ngắn nhất của dịch vụ. */
    private Optional<OffsetDateTime> resolveRecallDate(long appointmentId, OffsetDateTime now) {
        List<LocalDate> next = jdbc.queryForList(
                "SELECT next_appointment_date FROM treatment_record WHERE appointment_id = ? AND next_appointment_date IS NOT NULL",
                LocalDate.class, appointmentId);
        if (!next.isEmpty()) {
            return Optional.of(next.get(0).atTime(config.getTime("clinic_open_time", java.time.LocalTime.of(8, 0)))
                    .atZone(CareConstants.ZONE).toOffsetDateTime());
        }
        List<Integer> cycles = jdbc.queryForList("""
            SELECT DISTINCT s.followup_cycle_days FROM appointment_service aps
              JOIN service s ON s.service_id = aps.service_id
             WHERE aps.appointment_id = ? AND s.followup_cycle_days IS NOT NULL
               AND (aps.is_performed OR NOT EXISTS (SELECT 1 FROM appointment_service x
                                                     WHERE x.appointment_id = aps.appointment_id AND x.is_performed))
            """, Integer.class, appointmentId);
        return cycles.stream().min(Integer::compare)
                .map(days -> workingTime.rollIntoWorkingHours(now.plusDays(days)));
    }
}
