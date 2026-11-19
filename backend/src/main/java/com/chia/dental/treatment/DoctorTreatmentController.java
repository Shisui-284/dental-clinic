package com.chia.dental.treatment;

import com.chia.dental.common.response.ApiResponse;
import com.chia.dental.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doctor/appointments")
@RequiredArgsConstructor
@Tag(name = "13. Doctor - Hoàn tất khám (UC-14 tối thiểu)",
        description = "Bác sĩ phụ trách ghi kết quả điều trị và hoàn tất lịch Đã đến; sự kiện này kích hoạt sinh công việc CSKH")
public class DoctorTreatmentController {

    private final TreatmentCompletionService service;

    @PatchMapping("/{appointmentId}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Hoàn tất lịch Đã đến: ghi kết quả điều trị, ngày tái khám đề xuất, dịch vụ đã thực hiện → sinh việc CSKH")
    public ApiResponse<Void> complete(@PathVariable long appointmentId, @AuthenticationPrincipal UserPrincipal actor,
                                      @RequestBody TreatmentCompletionService.CompleteRequest req) {
        service.complete(appointmentId, actor, req);
        return ApiResponse.ok("Đã hoàn tất lịch khám", null);
    }
}
