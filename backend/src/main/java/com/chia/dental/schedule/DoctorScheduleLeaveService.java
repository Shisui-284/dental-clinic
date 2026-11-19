package com.chia.dental.schedule;

import com.chia.dental.common.audit.AuditLogService;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.DentalChair;
import com.chia.dental.entity.DoctorLeave;
import com.chia.dental.entity.DoctorSchedule;
import com.chia.dental.entity.StaffAccount;
import com.chia.dental.repository.DentalChairRepository;
import com.chia.dental.repository.DoctorLeaveRepository;
import com.chia.dental.repository.DoctorScheduleRepository;
import com.chia.dental.repository.StaffAccountRepository;
import com.chia.dental.schedule.ScheduleDtos.*;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DoctorScheduleLeaveService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DoctorScheduleRepository doctorScheduleRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final DentalChairRepository dentalChairRepository;
    private final LeaveConflictService leaveConflictService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ChairDto> listActiveChairs() {
        return dentalChairRepository.findByStatus("ACTIVE").stream()
                .map(c -> ChairDto.builder()
                        .chairId(c.getChairId())
                        .name(c.getChairName())
                        .status(c.getStatus())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleDto> listSchedules(
            UserPrincipal principal,
            Long staffId,
            LocalDate fromDate,
            LocalDate toDate,
            String status) {
        // Bác sĩ chỉ xem lịch ca làm cá nhân tại B1 (5.3.4)
        Long effectiveStaffId = resolveScopedDoctorId(principal, staffId);
        long staffFilter = effectiveStaffId != null ? effectiveStaffId : -1L;
        LocalDate from = fromDate != null ? fromDate : LocalDate.of(1900, 1, 1);
        LocalDate to = toDate != null ? toDate : LocalDate.of(2999, 12, 31);
        String statusFilter = status != null ? status.trim() : "";
        return doctorScheduleRepository.searchSchedules(staffFilter, from, to, statusFilter).stream()
                .map(this::toScheduleDto)
                .toList();
    }

    @Transactional
    public ScheduleDto createSchedule(UserPrincipal principal, CreateScheduleRequest req) {
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            throw new BusinessException("INVALID_TIME_RANGE", "Giờ kết thúc ca phải lớn hơn giờ bắt đầu.", HttpStatus.BAD_REQUEST);
        }

        StaffAccount doctor = staffAccountRepository.findById(req.getStaffId())
                .filter(s -> Boolean.TRUE.equals(s.getStatus()) && "DOCTOR".equals(s.getRole().getRoleName()))
                .orElseThrow(() -> new BusinessException("DOCTOR_NOT_FOUND", "Bác sĩ không tồn tại hoặc đã ngừng hoạt động.", HttpStatus.NOT_FOUND));

        if (doctorScheduleRepository.existsByStaffStaffIdAndShiftDateAndStartTime(
                doctor.getStaffId(), req.getShiftDate(), req.getStartTime())) {
            throw new BusinessException("SCHEDULE_ALREADY_EXISTS", "Ca làm việc tại mốc giờ này đã tồn tại.", HttpStatus.CONFLICT);
        }

        DoctorSchedule saved = doctorScheduleRepository.saveAndFlush(DoctorSchedule.builder()
                .staff(doctor)
                .shiftDate(req.getShiftDate())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .status("ACTIVE")
                .build());

        auditLogService.logStaff(
                principal.getId(),
                "SCHEDULE_CREATE",
                "doctor_schedule",
                saved.getScheduleId(),
                null,
                Map.of("staffId", doctor.getStaffId(), "shiftDate", saved.getShiftDate().toString(),
                        "startTime", saved.getStartTime().toString(), "endTime", saved.getEndTime().toString())
        );

        return toScheduleDto(saved);
    }

    @Transactional
    public DoctorLeaveDto requestLeave(UserPrincipal principal, DoctorLeaveRequest req) {
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            throw new BusinessException("INVALID_TIME_RANGE", "Giờ kết thúc nghỉ phải lớn hơn giờ bắt đầu.", HttpStatus.BAD_REQUEST);
        }

        Long doctorId = "DOCTOR".equals(principal.getRole())
                ? principal.getId()
                : (req.getStaffId() != null ? req.getStaffId() : principal.getId());

        StaffAccount doctor = staffAccountRepository.findById(doctorId)
                .filter(s -> Boolean.TRUE.equals(s.getStatus()) && "DOCTOR".equals(s.getRole().getRoleName()))
                .orElseThrow(() -> new BusinessException("DOCTOR_NOT_FOUND", "Bác sĩ không hợp lệ.", HttpStatus.NOT_FOUND));

        DoctorLeave leave = doctorLeaveRepository.saveAndFlush(DoctorLeave.builder()
                .staff(doctor)
                .leaveDate(req.getLeaveDate())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .reason(req.getReason().trim())
                .status("PENDING")
                .build());

        auditLogService.logStaff(
                principal.getId(),
                "DOCTOR_LEAVE_REQUEST",
                "doctor_leave",
                leave.getLeaveId(),
                null,
                Map.of("staffId", doctor.getStaffId(), "leaveDate", leave.getLeaveDate().toString(),
                        "startTime", leave.getStartTime().toString(), "endTime", leave.getEndTime().toString(),
                        "reason", leave.getReason(), "status", "PENDING")
        );

        return toLeaveDto(leave);
    }

    @Transactional(readOnly = true)
    public List<DoctorLeaveDto> listLeaves(
            UserPrincipal principal,
            Long staffId,
            String status,
            LocalDate fromDate,
            LocalDate toDate) {
        Long effectiveStaffId = resolveScopedDoctorId(principal, staffId);
        long staffFilter = effectiveStaffId != null ? effectiveStaffId : -1L;
        String statusFilter = status != null ? status.trim() : "";
        LocalDate from = fromDate != null ? fromDate : LocalDate.of(1900, 1, 1);
        LocalDate to = toDate != null ? toDate : LocalDate.of(2999, 12, 31);
        return doctorLeaveRepository.searchLeaves(staffFilter, statusFilter, from, to).stream()
                .map(this::toLeaveDto)
                .toList();
    }

    @Transactional
    public LeaveApprovalResponse approveOrRejectLeave(Long leaveId, UserPrincipal principal, LeaveApprovalRequest req) {
        DoctorLeave leave = doctorLeaveRepository.findByIdWithDetails(leaveId)
                .orElseThrow(() -> new BusinessException("LEAVE_NOT_FOUND", "Không tìm thấy đơn xin nghỉ #" + leaveId, HttpStatus.NOT_FOUND));

        if (!"PENDING".equals(leave.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Chỉ có thể duyệt/từ chối đơn nghỉ đang ở trạng thái PENDING (hiện tại: " + leave.getStatus() + ").",
                    HttpStatus.BAD_REQUEST);
        }

        StaffAccount approver = staffAccountRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "Không tìm thấy thông tin người duyệt.", HttpStatus.NOT_FOUND));

        String decision = req.getDecision();
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);

        leave.setStatus(decision);
        leave.setApprovedBy(approver);
        leave.setApprovedAt(now);
        leave = doctorLeaveRepository.saveAndFlush(leave);

        List<LeaveConflictDto> conflicts = "APPROVED".equals(decision)
                ? leaveConflictService.findConflicts(null, leave.getLeaveId())
                : List.of();

        auditLogService.logStaff(
                principal.getId(),
                "APPROVED".equals(decision) ? "DOCTOR_LEAVE_APPROVE" : "DOCTOR_LEAVE_REJECT",
                "doctor_leave",
                leave.getLeaveId(),
                Map.of("status", "PENDING"),
                Map.of("status", decision, "approvedBy", approver.getStaffId(), "conflictCount", conflicts.size())
        );

        return LeaveApprovalResponse.builder()
                .leave(toLeaveDto(leave))
                .conflictCount(conflicts.size())
                .affectedAppointments(conflicts)
                .build();
    }

    @Transactional(readOnly = true)
    public List<LeaveConflictDto> getLeaveConflicts(UserPrincipal principal, Long staffId, Long leaveId) {
        Long effectiveStaffId = resolveScopedDoctorId(principal, staffId);
        return leaveConflictService.findConflicts(effectiveStaffId, leaveId);
    }

    private Long resolveScopedDoctorId(UserPrincipal principal, Long requestedStaffId) {
        if ("DOCTOR".equals(principal.getRole())) {
            if (requestedStaffId != null && !requestedStaffId.equals(principal.getId())) {
                throw new BusinessException(
                        "ACCESS_DENIED_SCOPE",
                        "Bác sĩ chỉ được xem dữ liệu lịch làm việc của chính mình.",
                        HttpStatus.FORBIDDEN
                );
            }
            return principal.getId();
        }
        return requestedStaffId;
    }

    private ScheduleDto toScheduleDto(DoctorSchedule s) {
        return ScheduleDto.builder()
                .scheduleId(s.getScheduleId())
                .staffId(s.getStaff().getStaffId())
                .doctorName(s.getStaff().getFullName())
                .shiftDate(s.getShiftDate())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .status(s.getStatus())
                .build();
    }

    private DoctorLeaveDto toLeaveDto(DoctorLeave l) {
        return DoctorLeaveDto.builder()
                .leaveId(l.getLeaveId())
                .staffId(l.getStaff().getStaffId())
                .doctorName(l.getStaff().getFullName())
                .leaveDate(l.getLeaveDate())
                .startTime(l.getStartTime())
                .endTime(l.getEndTime())
                .reason(l.getReason())
                .status(l.getStatus())
                .approvedById(l.getApprovedBy() != null ? l.getApprovedBy().getStaffId() : null)
                .approvedByName(l.getApprovedBy() != null ? l.getApprovedBy().getFullName() : null)
                .approvedAt(l.getApprovedAt())
                .build();
    }
}

