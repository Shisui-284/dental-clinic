package com.chia.dental.care;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình kỹ thuật phân hệ CSKH (tham số nghiệp vụ nằm ở bảng clinic_config).
 * Chu kỳ các job nằm chung nhóm app.scheduling.* cùng HoldExpirationJob; công tắc bật/tắt là app.scheduling.enabled.
 */
@Data
@ConfigurationProperties(prefix = "app.care")
public class CareProperties {

    /** URL gốc trang đánh giá (K9) của frontend; link gửi khách = feedbackBaseUrl + "/" + token. */
    private String feedbackBaseUrl = "http://localhost:5173/feedback";

    /** Số việc gửi tin xử lý tối đa mỗi lượt quét. */
    private int batchSize = 50;
}
