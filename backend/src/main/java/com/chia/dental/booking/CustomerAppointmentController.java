package com.chia.dental.booking;

import com.chia.dental.booking.AppointmentDtos.*;
import com.chia.dental.common.response.ApiResponse;
import com.chia.dental.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/appointments")
@RequiredArgsConstructor
@Tag(name = "3. Customer Booking", description = "API Khách hàng: Đặt lịch trực tuyến & Xem lịch hẹn của bản thân")
public class CustomerAppointmentController {

    private final AppointmentBookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Khách hàng đặt lịch trực tuyến (tạo bản ghi PENDING)")
    public ApiResponse<AppointmentResponse> createAppointment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAppointmentRequest req) {
        return ApiResponse.ok("Đặt lịch thành công, đang chờ lễ tân xác nhận",
                bookingService.createCustomerAppointment(principal, req));
    }

    @GetMapping
    @Operation(summary = "Xem danh sách lịch hẹn của khách hàng đang đăng nhập")
    public ApiResponse<List<AppointmentResponse>> getMyAppointments(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(bookingService.getCustomerAppointments(principal));
    }
}

