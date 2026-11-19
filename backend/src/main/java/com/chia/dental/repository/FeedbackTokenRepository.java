package com.chia.dental.repository;

import com.chia.dental.entity.FeedbackToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FeedbackTokenRepository extends JpaRepository<FeedbackToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FeedbackToken> findByTokenHash(String tokenHash);

    /** Đọc không khóa (dùng cho GET xem thông tin link). */
    @Query("select t from FeedbackToken t where t.tokenHash = :hash")
    Optional<FeedbackToken> peekByTokenHash(@Param("hash") String hash);

    Optional<FeedbackToken> findByAppointmentId(Long appointmentId);

    void deleteByAppointmentIdAndUsedAtIsNull(Long appointmentId);
}
