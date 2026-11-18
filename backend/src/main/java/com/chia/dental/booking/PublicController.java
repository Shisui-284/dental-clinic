package com.chia.dental.booking;

import com.chia.dental.booking.PublicDtos.*;
import com.chia.dental.common.response.ApiResponse;
import com.chia.dental.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "2. Public Catalog & Slots", description = "API công khai: Danh mục dịch vụ, Bác sĩ và Khung giờ trống động")
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
    @Operation(summary = "Tính thời lượng (kèm đệm ghế + đệm hồ sơ mới 15p) và lọc khung giờ trống động")
    public ApiResponse<AvailableSlotsResponse> getAvailableSlots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam List<Long> serviceIds,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String patientFullName,
            @RequestParam(required = false) Short birthYear,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Boolean isNewPatient,
            @AuthenticationPrincipal UserPrincipal principal) {
        LocalDate start = fromDate != null ? fromDate : date;
        LocalDate end = toDate != null ? toDate : start;
        Long effectiveStaffId = staffId != null ? staffId : doctorId;
        return ApiResponse.ok(publicCatalogService.getAvailableSlots(
                start, end, serviceIds, effectiveStaffId,
                phone, patientFullName, birthYear, patientId, isNewPatient, principal));
    }
}
