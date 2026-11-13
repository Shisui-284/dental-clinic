package com.chia.dental.repository;

import com.chia.dental.entity.DoctorLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, Long> {
    List<DoctorLeave> findByStaffStaffIdAndLeaveDateAndStatus(Long staffId, LocalDate leaveDate, String status);
}

