package com.chia.dental.repository;

import com.chia.dental.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
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

    @Query("""
        SELECT ds FROM DoctorSchedule ds
        JOIN FETCH ds.staff s
        JOIN FETCH s.role r
        WHERE (:staffId = -1L OR s.staffId = :staffId)
          AND ds.shiftDate >= :fromDate
          AND ds.shiftDate <= :toDate
          AND (:status = '' OR ds.status = :status)
        ORDER BY ds.shiftDate ASC, ds.startTime ASC, s.staffId ASC
    """)
    List<DoctorSchedule> searchSchedules(
            @Param("staffId") long staffId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("status") String status);

    boolean existsByStaffStaffIdAndShiftDateAndStartTime(Long staffId, LocalDate shiftDate, LocalTime startTime);
}
