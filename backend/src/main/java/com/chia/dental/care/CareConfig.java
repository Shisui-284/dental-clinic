package com.chia.dental.care;

import com.chia.dental.care.notification.SmsProperties;
import com.chia.dental.care.notification.ZaloProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Đăng ký cấu hình CSKH. @EnableScheduling đã có ở config.SchedulingConfig của dự án. */
@Configuration
@EnableConfigurationProperties({CareProperties.class, ZaloProperties.class, SmsProperties.class})
public class CareConfig {
}
