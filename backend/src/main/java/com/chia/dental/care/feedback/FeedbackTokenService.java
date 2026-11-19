package com.chia.dental.care.feedback;

import com.chia.dental.care.support.CareSettings;
import com.chia.dental.entity.FeedbackToken;
import com.chia.dental.repository.FeedbackTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

/** Token dùng một lần cho link đánh giá K9 (không cần đăng nhập). Chỉ lưu SHA-256 của token. */
@Service
@RequiredArgsConstructor
public class FeedbackTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final FeedbackTokenRepository repository;
    private final CareSettings config;

    /**
     * Cấp token mới cho lịch hẹn (thay token chưa dùng cũ nếu có — dùng khi gửi lại tin).
     * @return token thô để nhúng vào link, hoặc empty nếu token của lịch này đã được dùng.
     */
    @Transactional
    public Optional<String> issue(Long appointmentId, OffsetDateTime now) {
        Optional<FeedbackToken> existing = repository.findByAppointmentId(appointmentId);
        if (existing.isPresent() && existing.get().getUsedAt() != null) return Optional.empty();
        repository.deleteByAppointmentIdAndUsedAtIsNull(appointmentId);
        repository.flush();

        byte[] bytes = new byte[16]; // 128-bit, 22 ký tự base64url — đủ ngắn cho SMS
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(FeedbackToken.builder()
                .appointmentId(appointmentId)
                .tokenHash(sha256(raw))
                .createdAt(now)
                .expiresAt(now.plusDays(config.getInt("feedback_link_ttl_days", 14)))
                .build());
        return Optional.of(raw);
    }

    /** Tìm và KHÓA bản ghi token (dùng trong transaction gửi đánh giá để chống gửi song song). */
    @Transactional
    public Optional<FeedbackToken> findForUpdate(String rawToken) {
        if (rawToken == null || rawToken.length() < 16 || rawToken.length() > 64) return Optional.empty();
        return repository.findByTokenHash(sha256(rawToken));
    }

    /** Tìm token không khóa (xem thông tin link). */
    @Transactional(readOnly = true)
    public Optional<FeedbackToken> peek(String rawToken) {
        if (rawToken == null || rawToken.length() < 16 || rawToken.length() > 64) return Optional.empty();
        return repository.peekByTokenHash(sha256(rawToken));
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
