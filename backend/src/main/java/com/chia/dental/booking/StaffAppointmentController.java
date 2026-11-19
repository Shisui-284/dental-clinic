package com.chia.dental.booking;

import com.chia.dental.booking.AppointmentDtos.*;
import com.chia.dental.common.response.ApiResponse;
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
@RequestMapping("/api/v1/staff/appointments")
@RequiredArgsConstructor
@Tag(name = "4. Staff & Receptionist (L1–L3)", description = "Điều phối lịch hẹn: Tạo lịch hộ/vãng lai/cấp cứu, Duyệt, Đổi, Check-in, Hủy, No-show & Hoàn tác")
public class StaffAppointmentController {

    private final AppointmentBookingService bookingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','DOCTOR')")
    @Operation(summary = "Tra cứu danh sách lịch hẹn theo bộ lọc (Bác sĩ chỉ xem được lịch của chính mình)")
    public ApiResponse<List<AppointmentResponse>> listAppointments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long chairId,
            @RequestParam(required = false) Boolean onlyLeaveConflict) {
        Long effectiveStaffId = staffId != null ? staffId : doctorId;
        return ApiResponse.ok(bookingService.getStaffAppointments(principal, date, status, effectiveStaffId, chairId, onlyLeaveConflict));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Lễ tân tạo lịch hộ (PHONE/SOCIAL -> CONFIRMED, bỏ qua Blacklist), vãng lai (WALK_IN -> CHECKED_IN + dịch vụ tạm) hoặc cấp cứu (is_priority)")
    public ApiResponse<AppointmentResponse> createStaffAppointment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StaffCreateAppointmentRequest req) {
        return ApiResponse.ok("Tạo lịch hẹn tại quầy thành công",
                bookingService.createStaffAppointment(principal, req));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Lễ tân duyệt lịch PENDING sang CONFIRMED (tự hủy nếu quá hold_until, chặn nếu bác sĩ nghỉ APPROVED)")
    public ApiResponse<AppointmentResponse> confirmAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) ConfirmAppointmentRequest req) {
        return ApiResponse.ok("Đã xác nhận lịch hẹn thành công",
                bookingService.confirmAppointment(id, principal, req));
    }

    @PutMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Lễ tân đổi ngày giờ, bác sĩ hoặc ghế nha tự do (bao gồm xử lý lịch trùng ca nghỉ bác sĩ)")
    public ApiResponse<AppointmentResponse> rescheduleAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StaffRescheduleRequest req) {
        return ApiResponse.ok("Đổi lịch hẹn thành công",
                bookingService.staffRescheduleAppointment(id, principal, req));
    }

    @PatchMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Lễ tân ghi nhận khách đã đến (CONFIRMED -> CHECKED_IN, ghi actual_arrival_at)")
    public ApiResponse<AppointmentResponse> checkInAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Đã cập nhật trạng thái khách Đã đến (CHECKED_IN)",
                bookingService.checkInAppointment(id, principal));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Lễ tân hủy lịch hẹn (bắt buộc nhập lý do)")
    public ApiResponse<AppointmentResponse> cancelAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CancelAppointmentRequest req) {
        return ApiResponse.ok("Đã hủy lịch hẹn",
                bookingService.staffCancelAppointment(id, principal, req));
    }

    @PatchMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Lễ tân đánh dấu khách Không đến (NO_SHOW), ghi no_show_event & trigger tự động tính Blacklist")
    public ApiResponse<AppointmentResponse> markNoShow(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Đã đánh dấu khách Không đến (NO_SHOW)",
                bookingService.markNoShow(id, principal));
    }

    @PostMapping("/{id}/reverse-no-show")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Lễ tân hoàn tác NO_SHOW -> CHECKED_IN trong cùng ngày khám (bắt buộc nhập reversedReason)")
    public ApiResponse<AppointmentResponse> reverseNoShow(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReverseNoShowRequest req) {
        return ApiResponse.ok("Đã hoàn tác trạng thái Không đến sang Đã đến (CHECKED_IN)",
                bookingService.reverseNoShow(id, principal, req));
    }
}
