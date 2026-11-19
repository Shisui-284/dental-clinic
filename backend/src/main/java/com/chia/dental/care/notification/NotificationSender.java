package com.chia.dental.care.notification;

/**
 * Adapter nhà cung cấp gửi tin (SRS 4.3.4 #4, NFR-12): logic nghiệp vụ không phụ thuộc cứng một nhà cung cấp.
 * Thêm nhà cung cấp SMS Brandname thật = viết một bean mới có channel() = "SMS".
 */
public interface NotificationSender {

    /** ZALO_ZNS hoặc SMS (khớp notification_log.channel). */
    String channel();

    /** Không được ném exception ra ngoài: mọi lỗi phải quy về SendResult. */
    SendResult send(OutboundMessage message);
}
