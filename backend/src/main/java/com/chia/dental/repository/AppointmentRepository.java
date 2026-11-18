package com.chia.dental.repository;

import com.chia.dental.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.patient p
        JOIN FETCH a.staff s
        JOIN FETCH a.chair c
        WHERE a.appointmentId = :appointmentId
    """)
    Optional<Appointment> findByIdWithDetails(@Param("appointmentId") Long appointmentId);

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
        WHERE (:status = '' OR a.status = :status)
          AND (:staffId = -1L OR s.staffId = :staffId)
          AND (:chairId = -1L OR c.chairId = :chairId)
          AND a.dateTime >= :startOfDay
          AND a.dateTime < :endOfDay
        ORDER BY a.isPriority DESC, a.dateTime DESC
    """)
    List<Appointment> searchStaffAppointments(
            @Param("status") String status,
            @Param("staffId") long staffId,
            @Param("chairId") long chairId,
            @Param("startOfDay") OffsetDateTime startOfDay,
            @Param("endOfDay") OffsetDateTime endOfDay);

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.staff s
        JOIN FETCH a.chair c
        WHERE a.status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN')
          AND a.dateTime >= :startOfDay
          AND a.dateTime < :endOfDay
    """)
    List<Appointment> findActiveAppointmentsInRange(
            @Param("startOfDay") OffsetDateTime startOfDay,
            @Param("endOfDay") OffsetDateTime endOfDay);

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.staff s
        WHERE a.patient.customer.customerId = :customerId
          AND a.status IN ('COMPLETED', 'CONFIRMED', 'CHECKED_IN')
          AND s.status = true
        ORDER BY a.dateTime DESC
    """)
    List<Appointment> findRecentAppointmentsForDoctorSuggestion(@Param("customerId") Long customerId);

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.status = 'PENDING'
          AND a.holdUntil IS NOT NULL
          AND a.holdUntil <= :now
    """)
    List<Appointment> findExpiredPendingAppointments(@Param("now") OffsetDateTime now);
}
