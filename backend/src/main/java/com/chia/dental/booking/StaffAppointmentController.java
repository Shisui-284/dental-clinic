package com.chia.dental.booking;

import com.chia.dental.booking.AppointmentDtos.*;
import com.chia.dental.common.response.ApiResponse;
import com.chia.dental.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff/appointments")
@RequiredArgsConstructor
@Tag(name = "4. Staff & Receptionist", description = "API Nhân sự: Tra cứu lịch hẹn & Lễ tân duyệt lịch")
public class StaffAppointmentController {

    private final AppointmentBookingService bookingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','DOCTOR')")
    @Operation(summary = "Tra cứu danh sách lịch hẹn (có thể lọc theo status: PENDING, CONFIRMED...)")
    public ApiResponse<List<AppointmentResponse>> listAppointments(@RequestParam(required = false) String status) {
        return ApiResponse.ok(bookingService.getStaffAppointments(status));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Lễ tân duyệt lịch hẹn từ PENDING sang CONFIRMED")
    public ApiResponse<AppointmentResponse> confirmAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) ConfirmAppointmentRequest req) {
        return ApiResponse.ok("Đã xác nhận lịch hẹn thành công",
                bookingService.confirmAppointment(id, principal, req));
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
}

