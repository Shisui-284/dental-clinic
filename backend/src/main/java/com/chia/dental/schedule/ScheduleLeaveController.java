package com.chia.dental.schedule;

import com.chia.dental.common.response.ApiResponse;
import com.chia.dental.schedule.ScheduleDtos.*;
import com.chia.dental.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "5. Doctor Schedules & Leaves (B1, Q1)", description = "Quản lý ca làm việc, gửi/duyệt đơn nghỉ bác sĩ & cảnh báo lịch hẹn trùng nghỉ")
public class ScheduleLeaveController {

    private final DoctorScheduleLeaveService scheduleLeaveService;

    @GetMapping("/staff/schedules")
    @PreAuthorize("hasAnyRole('DOCTOR','RECEPTIONIST','MANAGER')")
    @Operation(summary = "Xem lịch ca làm việc của bác sĩ (Bác sĩ chỉ xem ca cá nhân; Lễ tân/Quản lý xem toàn bộ)")
    public ApiResponse<List<ScheduleDto>> listSchedules(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(scheduleLeaveService.listSchedules(principal, staffId, fromDate, toDate, status));
    }

    @PostMapping("/staff/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Tạo ca làm việc mới cho bác sĩ")
    public ApiResponse<ScheduleDto> createSchedule(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateScheduleRequest req) {
        return ApiResponse.ok("Tạo ca làm việc thành công", scheduleLeaveService.createSchedule(principal, req));
    }

    @PostMapping("/doctor/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DOCTOR','MANAGER')")
    @Operation(summary = "Bác sĩ gửi yêu cầu nghỉ phép / nghỉ đột xuất (trạng thái PENDING)")
    public ApiResponse<DoctorLeaveDto> requestLeave(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DoctorLeaveRequest req) {
        return ApiResponse.ok("Đã gửi yêu cầu xin nghỉ, chờ quản lý phê duyệt",
                scheduleLeaveService.requestLeave(principal, req));
    }

    @GetMapping("/staff/leaves")
    @PreAuthorize("hasAnyRole('DOCTOR','RECEPTIONIST','MANAGER')")
    @Operation(summary = "Tra cứu danh sách đơn xin nghỉ bác sĩ")
    public ApiResponse<List<DoctorLeaveDto>> listLeaves(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.ok(scheduleLeaveService.listLeaves(principal, staffId, status, fromDate, toDate));
    }

    @GetMapping("/staff/chairs")
    @PreAuthorize("hasAnyRole('DOCTOR','RECEPTIONIST','MANAGER')")
    @Operation(summary = "Danh sách ghế nha khoa đang hoạt động (ACTIVE) dùng cho màn L1/L3")
    public ApiResponse<List<ChairDto>> listActiveChairs() {
        return ApiResponse.ok(scheduleLeaveService.listActiveChairs());
    }

    @PatchMapping("/manager/leaves/{id}/approval")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Quản lý duyệt/từ chối đơn nghỉ bác sĩ; trả kèm danh sách lịch CONFIRMED bị ảnh hưởng từ View v_appointment_leave_conflict")
    public ApiResponse<LeaveApprovalResponse> approveOrRejectLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LeaveApprovalRequest req) {
        LeaveApprovalResponse result = scheduleLeaveService.approveOrRejectLeave(id, principal, req);
        String msg = "APPROVED".equals(req.getDecision())
                ? (result.getConflictCount() > 0
                    ? "Đã duyệt lịch nghỉ — CẢNH BÁO có " + result.getConflictCount() + " lịch hẹn bị ảnh hưởng!"
                    : "Đã duyệt lịch nghỉ thành công")
                : "Đã từ chối đơn xin nghỉ";
        return ApiResponse.ok(msg, result);
    }

    @GetMapping("/staff/appointments/leave-conflicts")
    @PreAuthorize("hasAnyRole('DOCTOR','RECEPTIONIST','MANAGER')")
    @Operation(summary = "Truy vấn View v_appointment_leave_conflict cảnh báo các lịch CONFIRMED trùng ca nghỉ APPROVED")
    public ApiResponse<List<LeaveConflictDto>> getLeaveConflicts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long leaveId) {
        return ApiResponse.ok(scheduleLeaveService.getLeaveConflicts(principal, staffId, leaveId));
    }
}

