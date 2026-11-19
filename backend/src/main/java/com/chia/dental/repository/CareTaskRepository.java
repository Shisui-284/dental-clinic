package com.chia.dental.repository;

import com.chia.dental.entity.CareTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareTaskRepository extends JpaRepository<CareTask, Long> {
    boolean existsByAppointmentIdAndTaskTypeAndSource(Long appointmentId, String taskType, String source);
}
