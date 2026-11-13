package com.chia.dental.repository;

import com.chia.dental.entity.AppointmentServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppointmentServiceRepository extends JpaRepository<AppointmentServiceEntity, Long> {
    @Query("""
        SELECT aps FROM AppointmentServiceEntity aps
        LEFT JOIN FETCH aps.service
        WHERE aps.appointment.appointmentId = :appointmentId
    """)
    List<AppointmentServiceEntity> findByAppointmentId(@Param("appointmentId") Long appointmentId);
}

