package com.chia.dental.repository;

import com.chia.dental.entity.NoShowEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NoShowEventRepository extends JpaRepository<NoShowEvent, Long> {

    Optional<NoShowEvent> findByAppointmentAppointmentId(Long appointmentId);

    @Query("""
        SELECT e FROM NoShowEvent e
        JOIN FETCH e.patient p
        JOIN FETCH e.appointment a
        LEFT JOIN FETCH e.reversedBy rb
        WHERE p.customer.customerId = :customerId
        ORDER BY e.occurredAt DESC
    """)
    List<NoShowEvent> findByCustomerIdWithDetails(@Param("customerId") Long customerId);

    @Query("""
        SELECT COUNT(e) FROM NoShowEvent e
        WHERE e.patient.customer.customerId = :customerId
          AND e.isReversed = false
          AND e.occurredAt >= :windowStart
    """)
    long countActiveInWindow(@Param("customerId") Long customerId, @Param("windowStart") OffsetDateTime windowStart);
}

