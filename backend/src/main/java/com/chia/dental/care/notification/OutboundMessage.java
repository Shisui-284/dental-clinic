package com.chia.dental.care.notification;

import java.util.Map;

/**
 * Nội dung gửi đi, độc lập nhà cung cấp.
 * {@code text}: nội dung hoàn chỉnh (dùng cho SMS và lưu notification_log).
 * {@code params}: tham số có tên để điền vào template ZNS.
 */
public record OutboundMessage(NotificationType type, String phone, String text,
                              Map<String, String> params, String trackingId) {
}
