package com.chia.dental.care;

import com.chia.dental.care.notification.NotificationJobService;
import com.chia.dental.care.task.CareTaskService;
import com.chia.dental.care.task.CareTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Các tiến trình nền của CSKH tự động hóa. Chạy được trên nhiều instance: tạo việc dùng ON CONFLICT,
 * nhận việc gửi dùng FOR UPDATE SKIP LOCKED, đánh dấu quá hạn là UPDATE idempotent.
 * Bật/tắt chung với các job khác bằng app.scheduling.enabled (xem config.SchedulingConfig).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareScheduler {

    private final NotificationJobService jobService;
    private final CareTaskService taskService;
    private final CareTriggerService triggerService;
    private final CareProperties props;

    /** Quét nhắc lịch 24h/2h rồi gửi mọi tin đến hạn. */
    @Scheduled(fixedDelayString = "${app.scheduling.care-dispatch-ms:60000}", initialDelay = 15_000)
    public void dispatch() {
        try {
            int queued = jobService.scanReminders();
            int processed = jobService.processDue(props.getBatchSize());
            if (queued > 0 || processed > 0) log.info("CSKH: tạo {} việc nhắc lịch, xử lý {} việc gửi tin", queued, processed);
        } catch (Exception e) {
            log.error("CSKH dispatch lỗi", e);
        }
    }

    /** Đánh dấu quá hạn SLA 48h làm việc. */
    @Scheduled(fixedDelayString = "${app.scheduling.care-overdue-scan-ms:300000}", initialDelay = 30_000)
    public void flagOverdue() {
        try {
            int n = taskService.flagOverdue();
            if (n > 0) log.info("CSKH: đánh dấu {} việc quá hạn SLA", n);
        } catch (Exception e) {
            log.error("CSKH flagOverdue lỗi", e);
        }
    }

    /** Lưới an toàn: sinh việc cho lịch đã hoàn tất mà sự kiện bị lỡ. */
    @Scheduled(fixedDelayString = "${app.scheduling.care-reconcile-ms:600000}", initialDelay = 60_000)
    public void reconcile() {
        try {
            int n = triggerService.reconcile(3);
            if (n > 0) log.info("CSKH: đối soát tạo thêm {} việc", n);
        } catch (Exception e) {
            log.error("CSKH reconcile lỗi", e);
        }
    }
}
