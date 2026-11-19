package com.chia.dental.care.notification;

/**
 * Loại thông báo (khớp notification_log.notif_type).
 * {@code careMessage = true}: tin CSKH/quảng cáo — bị chặn khi khách opt-out (SRS 2.2.9).
 * {@code false}: tin giao dịch (OTP, xác nhận/đổi/hủy lịch) — luôn được gửi.
 */
public enum NotificationType {
    OTP(false),
    APPOINTMENT_CONFIRMED(false),
    APPOINTMENT_RESCHEDULED(false),
    APPOINTMENT_CANCELLED(false),
    APPOINTMENT_REMINDER(true),
    POST_VISIT_CHECK(true),
    RECALL(true),
    BIRTHDAY(true);

    private final boolean careMessage;

    NotificationType(boolean careMessage) {
        this.careMessage = careMessage;
    }

    public boolean isCareMessage() {
        return careMessage;
    }
}
