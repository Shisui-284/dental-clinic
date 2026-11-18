package com.chia.dental.repository;

import com.chia.dental.entity.ClinicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClinicHolidayRepository extends JpaRepository<ClinicHoliday, Long> {
    boolean existsByHolidayDate(LocalDate holidayDate);
    Optional<ClinicHoliday> findByHolidayDate(LocalDate holidayDate);
    List<ClinicHoliday> findByHolidayDateBetween(LocalDate fromDate, LocalDate toDate);
}
