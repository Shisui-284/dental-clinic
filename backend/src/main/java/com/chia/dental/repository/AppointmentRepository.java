package com.chia.dental.repository;

import com.chia.dental.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.patient p
        JOIN FETCH a.staff s
        JOIN FETCH a.chair c
        WHERE p.customer.customerId = :customerId
        ORDER BY a.dateTime DESC
    """)
    List<Appointment> findByCustomerId(@Param("customerId") Long customerId);

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.patient p
        JOIN FETCH a.staff s
        JOIN FETCH a.chair c
        WHERE (:status IS NULL OR a.status = :status)
        ORDER BY a.dateTime DESC
    """)
    List<Appointment> findAllWithDetails(@Param("status") String status);

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN')
          AND a.dateTime >= :startOfDay
          AND a.dateTime < :endOfDay
    """)
    List<Appointment> findActiveAppointmentsInRange(
            @Param("startOfDay") OffsetDateTime startOfDay,
            @Param("endOfDay") OffsetDateTime endOfDay);
}

