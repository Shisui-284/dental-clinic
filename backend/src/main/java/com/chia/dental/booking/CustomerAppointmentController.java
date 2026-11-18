package com.chia.dental.booking;

import com.chia.dental.booking.AppointmentDtos.*;
import com.chia.dental.common.response.ApiResponse;
import com.chia.dental.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/appointments")
@RequiredArgsConstructor
@Tag(name = "3. Customer Booking", description = "API Khách hàng (Màn K5–K7): Đặt lịch, Đổi/Hủy >= 4h, Tải file .ics")
public class CustomerAppointmentController {

    private final AppointmentBookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Khách hàng đặt lịch trực tuyến (2h–30 ngày, tự phân bổ bác sĩ nếu trống, tính hold_until)")
    public ApiResponse<AppointmentResponse> createAppointment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAppointmentRequest req) {
        return ApiResponse.ok("Đã gửi yêu cầu đặt lịch, đang chờ lễ tân xác nhận",
                bookingService.createCustomerAppointment(principal, req));
    }

    @GetMapping
    @Operation(summary = "Xem danh sách lịch hẹn của khách hàng (tab: ALL | UPCOMING | PAST)")
    public ApiResponse<List<AppointmentResponse>> getMyAppointments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "ALL") String tab) {
        return ApiResponse.ok(bookingService.getCustomerAppointments(principal, tab));
    }

    @PutMapping("/{id}/reschedule")
    @Operation(summary = "Khách hàng tự đổi ngày giờ / bác sĩ khi còn >= 4 giờ trước giờ hẹn")
    public ApiResponse<AppointmentResponse> rescheduleAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CustomerRescheduleRequest req) {
        return ApiResponse.ok("Đổi lịch hẹn thành công",
                bookingService.rescheduleCustomerAppointment(id, principal, req));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Khách hàng tự hủy lịch hẹn khi còn >= 4 giờ trước giờ hẹn")
    public ApiResponse<AppointmentResponse> cancelAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CancelAppointmentRequest req) {
        return ApiResponse.ok("Hủy lịch hẹn thành công",
                bookingService.cancelCustomerAppointment(id, principal, req));
    }

    @GetMapping(value = "/{id}/ics", produces = "text/calendar; charset=UTF-8")
    @Operation(summary = "Tải tệp lịch .ics (iCalendar) để thêm lịch hẹn vào điện thoại")
    public ResponseEntity<byte[]> downloadIcs(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        var payload = bookingService.exportCustomerAppointmentIcs(id, principal);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "calendar", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(payload.filename()).build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(payload.content().getBytes(StandardCharsets.UTF_8));
    }
}
