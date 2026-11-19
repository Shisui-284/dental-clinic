package com.chia.dental.care.notification;

import java.time.OffsetDateTime;

/** Một dòng notification_job kèm SĐT khách. */
public record JobRow(long jobId, String notifType, long customerId, Long patientId, Long appointmentId,
                     Long careTaskId, OffsetDateTime targetTime, Integer reminderHours, int attempts,
                     String phone) {
}
