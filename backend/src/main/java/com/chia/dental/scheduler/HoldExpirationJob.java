package com.chia.dental.scheduler;

import com.chia.dental.common.audit.AuditLogService;
import com.chia.dental.entity.Appointment;
import com.chia.dental.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Background Job tự động quét và hủy các lịch hẹn PENDING quá hạn giữ chỗ hold_until (5.3.6 mục a.1).
 * Giải phóng tài nguyên bác sĩ và ghế nha, ghi audit_log (actor_type = 'SYSTEM'), không tính no_show_event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpirationJob {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AppointmentRepository appointmentRepository;
    private final AuditLogService auditLogService;

    @Scheduled(fixedDelayString = "${app.scheduling.hold-expiration-ms:60000}")
    @Transactional
    public int cancelExpiredPendingAppointments() {
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        List<Appointment> expiredList = appointmentRepository.findExpiredPendingAppointments(now);
        if (expiredList.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Appointment appt : expiredList) {
            String holdUntilStr = appt.getHoldUntil() != null ? appt.getHoldUntil().toString() : null;
            appt.setStatus("CANCELLED");
            appt.setCancelledByType("SYSTEM");
            appt.setCancelledBy(null);
            appt.setCancelReason("Hết hạn giữ chỗ");
            appointmentRepository.saveAndFlush(appt);

            auditLogService.logSystem(
                    "APPOINTMENT_AUTO_CANCEL",
                    "appointment",
                    appt.getAppointmentId(),
                    Map.of("status", "PENDING", "holdUntil", String.valueOf(holdUntilStr)),
                    Map.of("status", "CANCELLED", "cancelledByType", "SYSTEM", "cancelReason", "Hết hạn giữ chỗ")
            );
            count++;
        }

        log.info("[HoldExpirationJob] Đã tự động hủy {} lịch hẹn PENDING quá hạn hold_until.", count);
        return count;
    }
}

