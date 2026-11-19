package com.chia.dental.care.task;

import com.chia.dental.treatment.AppointmentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sinh việc CSKH SAU KHI hoàn tất khám đã commit. Lỗi CSKH không được làm hỏng nghiệp vụ khám;
 * nếu lỡ, CareScheduler.reconcile() sẽ bù.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentCompletedListener {

    private final CareTriggerService triggerService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(AppointmentCompletedEvent event) {
        try {
            triggerService.onAppointmentCompleted(event.appointmentId());
        } catch (Exception e) {
            log.error("Sinh việc CSKH cho lịch {} lỗi (sẽ được đối soát sau): {}", event.appointmentId(), e.getMessage(), e);
        }
    }
}
