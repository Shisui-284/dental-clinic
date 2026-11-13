package com.chia.dental.repository;

import com.chia.dental.entity.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRequestRepository extends JpaRepository<OtpRequest, Long> {
    Optional<OtpRequest> findTopByPhoneAndPurposeOrderByCreatedAtDesc(String phone, String purpose);
}

