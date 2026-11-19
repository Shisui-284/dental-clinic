package com.chia.dental.care.web;

import com.chia.dental.care.CareDtos.*;
import com.chia.dental.care.task.CareTaskService;
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
@RequestMapping("/api/v1/doctor/care")
@RequiredArgsConstructor
@Tag(name = "10. CSKH - Bác sĩ hỗ trợ chuyên môn (B2)",
        description = "Bác sĩ trả lời các việc CSKH lễ tân chuyển sang Cần hỗ trợ chuyên môn")
public class DoctorCareController {

    private final CareTaskService service;

    @GetMapping("/medical-support-tasks")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Việc đang chờ bác sĩ hỗ trợ chuyên môn (chỉ bệnh nhân của chính bác sĩ)")
    public ApiResponse<List<CareTaskView>> medicalSupportTasks(@AuthenticationPrincipal UserPrincipal doctor) {
        return ApiResponse.ok(service.medicalSupportTasksFor(doctor));
    }

    @PatchMapping("/tasks/{taskId}/medical-response")
    @PreAuthorize("hasAnyRole('DOCTOR','MANAGER')")
    @Operation(summary = "Bác sĩ phản hồi chuyên môn: Cần hỗ trợ chuyên môn → Đang xử lý (trả lại lễ tân)")
    public ApiResponse<CareTaskView> medicalResponse(@PathVariable long taskId, @AuthenticationPrincipal UserPrincipal actor,
                                                     @Valid @RequestBody MedicalResponseRequest req) {
        return ApiResponse.ok("Đã gửi phản hồi chuyên môn", service.medicalResponse(taskId, actor, req.note()));
    }
}
