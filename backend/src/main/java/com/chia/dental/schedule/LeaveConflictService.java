package com.chia.dental.schedule;

import com.chia.dental.schedule.ScheduleDtos.LeaveConflictDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LeaveConflictService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbcTemplate;

    public List<LeaveConflictDto> findConflicts(Long staffId, Long leaveId) {
        StringBuilder sql = new StringBuilder("""
            SELECT v.appointment_id,
                   v.patient_id,
                   p.full_name AS patient_name,
                   a.booked_by_phone,
                   v.staff_id,
                   s.full_name AS doctor_name,
                   v.date_time,
                   v.end_time,
                   v.leave_id,
                   l.leave_date,
                   l.start_time AS leave_start_time,
                   l.end_time AS leave_end_time,
                   l.reason AS leave_reason
              FROM v_appointment_leave_conflict v
              JOIN appointment a ON a.appointment_id = v.appointment_id
              JOIN patient_profile p ON p.patient_id = v.patient_id
              JOIN staff_account s ON s.staff_id = v.staff_id
              JOIN doctor_leave l ON l.leave_id = v.leave_id
             WHERE 1 = 1
            """);

        List<Object> params = new ArrayList<>();
        if (staffId != null) {
            sql.append(" AND v.staff_id = ?");
            params.add(staffId);
        }
        if (leaveId != null) {
            sql.append(" AND v.leave_id = ?");
            params.add(leaveId);
        }
        sql.append(" ORDER BY v.date_time ASC");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            long apptId = rs.getLong("appointment_id");
            OffsetDateTime dt = rs.getObject("date_time", OffsetDateTime.class);
            OffsetDateTime endDt = rs.getObject("end_time", OffsetDateTime.class);
            int year = dt.atZoneSameInstant(CLINIC_ZONE).getYear();
            return LeaveConflictDto.builder()
                    .appointmentId(apptId)
                    .appointmentCode(String.format("LH-%d-%06d", year, apptId))
                    .patientId(rs.getLong("patient_id"))
                    .patientName(rs.getString("patient_name"))
                    .bookedByPhone(rs.getString("booked_by_phone"))
                    .staffId(rs.getLong("staff_id"))
                    .doctorName(rs.getString("doctor_name"))
                    .dateTime(dt.atZoneSameInstant(CLINIC_ZONE).toOffsetDateTime())
                    .endTime(endDt.atZoneSameInstant(CLINIC_ZONE).toOffsetDateTime())
                    .leaveId(rs.getLong("leave_id"))
                    .leaveDate(rs.getDate("leave_date").toLocalDate())
                    .leaveStartTime(rs.getTime("leave_start_time").toLocalTime())
                    .leaveEndTime(rs.getTime("leave_end_time").toLocalTime())
                    .leaveReason(rs.getString("leave_reason"))
                    .build();
        }, params.toArray());
    }

    public Set<Long> findConflictingAppointmentIds() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT DISTINCT appointment_id FROM v_appointment_leave_conflict",
                Long.class
        );
        return new HashSet<>(ids);
    }
}

