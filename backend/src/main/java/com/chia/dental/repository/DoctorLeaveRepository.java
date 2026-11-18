package com.chia.dental.repository;

import com.chia.dental.entity.DoctorLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, Long> {

    List<DoctorLeave> findByStaffStaffIdAndLeaveDateAndStatus(Long staffId, LocalDate leaveDate, String status);

    @Query("""
        SELECT dl FROM DoctorLeave dl
        JOIN FETCH dl.staff s
        LEFT JOIN FETCH dl.approvedBy ab
        WHERE dl.leaveId = :leaveId
    """)
    Optional<DoctorLeave> findByIdWithDetails(@Param("leaveId") Long leaveId);

    @Query("""
        SELECT dl FROM DoctorLeave dl
        JOIN FETCH dl.staff s
        LEFT JOIN FETCH dl.approvedBy ab
        WHERE (:staffId = -1L OR s.staffId = :staffId)
          AND (:status = '' OR dl.status = :status)
          AND dl.leaveDate >= :fromDate
          AND dl.leaveDate <= :toDate
        ORDER BY dl.leaveDate DESC, dl.startTime ASC
    """)
    List<DoctorLeave> searchLeaves(
            @Param("staffId") long staffId,
            @Param("status") String status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
