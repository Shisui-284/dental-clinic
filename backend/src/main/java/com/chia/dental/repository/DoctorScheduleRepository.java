package com.chia.dental.repository;

import com.chia.dental.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    List<DoctorSchedule> findByShiftDateAndStatus(LocalDate shiftDate, String status);
    List<DoctorSchedule> findByStaffStaffIdAndShiftDateAndStatus(Long staffId, LocalDate shiftDate, String status);
}

