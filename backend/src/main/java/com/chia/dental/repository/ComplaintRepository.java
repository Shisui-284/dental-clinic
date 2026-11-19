package com.chia.dental.repository;

import com.chia.dental.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    Optional<Complaint> findByFeedbackId(Long feedbackId);
}
