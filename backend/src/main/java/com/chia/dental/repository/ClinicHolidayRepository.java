package com.chia.dental.repository;

import com.chia.dental.entity.ClinicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ClinicHolidayRepository extends JpaRepository<ClinicHoliday, Long> {
    boolean existsByHolidayDate(LocalDate holidayDate);
}

