package com.chia.dental.common.audit;

import com.chia.dental.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void logByPrincipal(
            UserPrincipal principal,
            String actionType,
            String targetEntity,
            Object targetId,
            Object oldValue,
            Object newValue) {
        if (principal == null) {
            logSystem(actionType, targetEntity, targetId, oldValue, newValue);
            return;
        }
        if ("STAFF".equals(principal.getType())) {
            logStaff(principal.getId(), actionType, targetEntity, targetId, oldValue, newValue);
        } else {
            logCustomer(principal.getId(), actionType, targetEntity, targetId, oldValue, newValue);
        }
    }

    public void logStaff(
            Long staffId,
            String actionType,
            String targetEntity,
            Object targetId,
            Object oldValue,
            Object newValue) {
        insert("STAFF", staffId, null, actionType, targetEntity, String.valueOf(targetId), oldValue, newValue, resolveClientIp());
    }

    public void logCustomer(
            Long customerId,
            String actionType,
            String targetEntity,
            Object targetId,
            Object oldValue,
            Object newValue) {
        insert("CUSTOMER", null, customerId, actionType, targetEntity, String.valueOf(targetId), oldValue, newValue, resolveClientIp());
    }

    public void logSystem(
            String actionType,
            String targetEntity,
            Object targetId,
            Object oldValue,
            Object newValue) {
        insert("SYSTEM", null, null, actionType, targetEntity, String.valueOf(targetId), oldValue, newValue, null);
    }

    private void insert(
            String actorType,
            Long actorStaffId,
            Long actorCustomerId,
            String actionType,
            String targetEntity,
            String targetId,
            Object oldValue,
            Object newValue,
            String ipAddress) {
        String sql = """
            INSERT INTO audit_log (
                actor_type, actor_staff_id, actor_customer_id,
                action_type, target_entity, target_id,
                old_value, new_value, ip_address, event_time
            ) VALUES (
                ?, ?, ?,
                ?, ?, ?,
                CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS inet), ?
            )
            """;

        jdbcTemplate.update(
                sql,
                actorType,
                actorStaffId,
                actorCustomerId,
                actionType,
                targetEntity,
                targetId,
                toJson(oldValue),
                toJson(newValue),
                sanitizeIp(ipAddress),
                OffsetDateTime.now(CLINIC_ZONE)
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Không thể serialize giá trị audit_log: {}", ex.getMessage());
            return null;
        }
    }

    private String resolveClientIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest req = servletAttrs.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String sanitizeIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return null;
        }
        String ip = rawIp.trim();
        if (ip.equalsIgnoreCase("localhost")) {
            return "127.0.0.1";
        }
        // Validate cơ bản định dạng IPv4 / IPv6 để tránh lỗi CAST(? AS inet)
        if (ip.matches("^[0-9a-fA-F:.]+$")) {
            return ip;
        }
        return null;
    }
}

