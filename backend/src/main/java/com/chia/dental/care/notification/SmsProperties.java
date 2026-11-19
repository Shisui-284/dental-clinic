package com.chia.dental.care.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {
    /** mock = chỉ ghi log, trả thành công giả (dùng khi chưa có nhà cung cấp SMS Brandname). */
    private String provider = "mock";
    private String brandname = "CHIA";
}
