package com.chia.dental.booking;

import com.chia.dental.booking.PublicDtos.*;
import com.chia.dental.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "2. Public Catalog & Slots", description = "API công khai: Danh mục dịch vụ, Bác sĩ và Khung giờ trống")
public class PublicController {

    private final PublicCatalogService publicCatalogService;

    @GetMapping("/services")
    @Operation(summary = "Lấy danh sách dịch vụ nha khoa đang hoạt động")
    public ApiResponse<List<ServiceDto>> getServices() {
        return ApiResponse.ok(publicCatalogService.getActiveServices());
    }

    @GetMapping("/doctors")
    @Operation(summary = "Lấy danh sách bác sĩ đang hoạt động")
    public ApiResponse<List<DoctorDto>> getDoctors() {
        return ApiResponse.ok(publicCatalogService.getActiveDoctors());
    }

    @GetMapping("/appointments/available-slots")
    @Operation(summary = "Tính toán khung giờ trống theo ngày, dịch vụ và bác sĩ (tùy chọn)")
    public ApiResponse<AvailableSlotsResponse> getAvailableSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam List<Long> serviceIds,
            @RequestParam(required = false) Long doctorId) {
        return ApiResponse.ok(publicCatalogService.getAvailableSlots(date, serviceIds, doctorId));
    }
}

