package com.chia.dental.repository;

import com.chia.dental.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    boolean existsByAppointmentId(Long appointmentId);
}
