package com.chia.dental.repository;

import com.chia.dental.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    @Query("""
        SELECT ds FROM DoctorSchedule ds
        JOIN FETCH ds.staff s
        JOIN FETCH s.role r
        WHERE ds.shiftDate = :shiftDate AND ds.status = :status
    """)
    List<DoctorSchedule> findByShiftDateAndStatus(@Param("shiftDate") LocalDate shiftDate, @Param("status") String status);

    @Query("""
        SELECT ds FROM DoctorSchedule ds
        JOIN FETCH ds.staff s
        JOIN FETCH s.role r
        WHERE s.staffId = :staffId AND ds.shiftDate = :shiftDate AND ds.status = :status
    """)
    List<DoctorSchedule> findByStaffStaffIdAndShiftDateAndStatus(
            @Param("staffId") Long staffId,
            @Param("shiftDate") LocalDate shiftDate,
            @Param("status") String status);
}
