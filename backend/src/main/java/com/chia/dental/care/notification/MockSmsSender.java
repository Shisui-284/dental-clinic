package com.chia.dental.care.notification;

import com.chia.dental.care.CareConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SMS Brandname GIẢ LẬP (quyết định chốt: SMS vẫn mock). Chỉ ghi log và trả thành công giả.
 * Khi có nhà cung cấp thật: tạo bean NotificationSender mới (channel "SMS") và đặt app.sms.provider khác "mock".
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsSender implements NotificationSender {

    @Override
    public String channel() {
        return CareConstants.CH_SMS;
    }

    @Override
    public SendResult send(OutboundMessage message) {
        log.info("[MOCK SMS] -> {} | {}", message.phone(), message.text());
        return SendResult.success("MOCK-" + UUID.randomUUID());
    }
}
