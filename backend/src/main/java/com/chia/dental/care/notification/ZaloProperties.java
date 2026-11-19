package com.chia.dental.care.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "app.zalo")
public class ZaloProperties {
    /** Bật gửi Zalo ZNS thật. Tắt/thiếu token => mọi lần gửi Zalo bị coi là từ chối và chuyển SMS dự phòng. */
    private boolean enabled = false;
    private String baseUrl = "https://business.openapi.zalo.me";
    /** Access token của Zalo OA (đặt qua biến môi trường ZALO_ACCESS_TOKEN). */
    private String accessToken;
    /** "development" để gửi thử không tính phí/không tới người dùng thật (theo tài liệu ZNS). Để trống khi chạy thật. */
    private String mode;
    private int timeoutMs = 5000;
    /** Map NotificationType -> template_id đã được Zalo duyệt. */
    private Map<String, String> templateIds = new HashMap<>();
}
