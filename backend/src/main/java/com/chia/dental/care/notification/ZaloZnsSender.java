package com.chia.dental.care.notification;

import com.chia.dental.care.CareConstants;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kênh ưu tiên: Zalo ZNS (SRS 2.2.5, FR-27).
 * POST {baseUrl}/message/template, header access_token; thành công khi error == 0 và có msg_id.
 *
 * LƯU Ý TRIỂN KHAI THẬT:
 *  - template_id và tên tham số template phải trùng với template đã đăng ký/duyệt trên Zalo (xem MessageComposer.params).
 *  - Access token Zalo OA có hạn; bản này đọc token tĩnh từ cấu hình, CHƯA tự refresh token.
 *  - Mã lỗi "SĐT không dùng Zalo" cụ thể cần đối chiếu tài liệu Zalo; hiện mọi phản hồi lỗi rõ ràng (error != 0)
 *    đều được coi là REJECTED và chuyển SMS theo SRS ("API trả về lỗi").
 */
@Slf4j
@Component
public class ZaloZnsSender implements NotificationSender {

    private final ZaloProperties props;
    private final RestClient client;

    public ZaloZnsSender(ZaloProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getTimeoutMs());
        factory.setReadTimeout(props.getTimeoutMs());
        this.client = RestClient.builder().baseUrl(props.getBaseUrl()).requestFactory(factory).build();
    }

    @Override
    public String channel() {
        return CareConstants.CH_ZALO;
    }

    @Override
    public SendResult send(OutboundMessage message) {
        if (!props.isEnabled() || props.getAccessToken() == null || props.getAccessToken().isBlank()) {
            return SendResult.rejected("ZALO_NOT_CONFIGURED", "Zalo ZNS chưa được bật/cấu hình access token");
        }
        String templateId = props.getTemplateIds().get(message.type().name());
        if (templateId == null || templateId.isBlank()) {
            return SendResult.rejected("ZALO_TEMPLATE_MISSING", "Chưa cấu hình template_id cho " + message.type());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", toZaloPhone(message.phone()));
        body.put("template_id", templateId);
        body.put("template_data", message.params());
        if (message.trackingId() != null) body.put("tracking_id", message.trackingId());
        if (props.getMode() != null && !props.getMode().isBlank()) body.put("mode", props.getMode());

        try {
            JsonNode resp = client.post()
                    .uri("/message/template")
                    .header("access_token", props.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null) return SendResult.unknown("ZALO_EMPTY_RESPONSE", "Phản hồi rỗng");
            int error = resp.path("error").asInt(-1);
            if (error == 0) {
                String msgId = resp.path("data").path("msg_id").asText(null);
                if (msgId == null || msgId.isBlank()) {
                    return SendResult.unknown("ZALO_NO_MSG_ID", "error=0 nhưng không có msg_id");
                }
                return SendResult.success(msgId);
            }
            return SendResult.rejected(String.valueOf(error), resp.path("message").asText(""));
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 429 || status.is5xxServerError()) {
                return SendResult.unknown("HTTP_" + status.value(), "Zalo tạm thời không phản hồi");
            }
            return SendResult.rejected("HTTP_" + status.value(), "Zalo từ chối yêu cầu");
        } catch (ResourceAccessException e) {
            return SendResult.unknown("ZALO_TIMEOUT", "Không kết nối/đọc được phản hồi từ Zalo");
        } catch (Exception e) {
            log.warn("Lỗi không xác định khi gọi Zalo ZNS", e);
            return SendResult.unknown("ZALO_ERROR", e.getClass().getSimpleName());
        }
    }

    /** 0912345678 / +84912345678 -> 84912345678 (định dạng ZNS yêu cầu). */
    static String toZaloPhone(String phone) {
        String p = phone.replaceAll("[^0-9]", "");
        if (p.startsWith("84")) return p;
        if (p.startsWith("0")) return "84" + p.substring(1);
        return p;
    }
}
