package com.chia.dental.care.web;

import com.chia.dental.care.CareDtos.*;
import com.chia.dental.care.CareQueryRepository;
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
@RequestMapping("/api/v1/staff/care")
@RequiredArgsConstructor
@Tag(name = "7. CSKH - Công việc & lịch sắp đến (L4–L6)",
        description = "Lễ tân/Quản lý: công việc chăm sóc (Chờ chăm sóc → Đang xử lý → Cần hỗ trợ chuyên môn → Đã đóng), SLA 48h làm việc, gọi xác nhận lịch, gửi tin mời tái khám")
public class CareTaskController {

    private final CareTaskService service;
    private final CareQueryRepository queries;

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Danh sách công việc CSKH (lọc theo trạng thái/loại/quá hạn/người phụ trách; mặc định chỉ việc đã đến hạn)")
    public ApiResponse<PageResult<CareTaskView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(defaultValue = "false") boolean includeNotYetDue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(status, type, overdue, assigneeId, includeNotYetDue, page, size));
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Chi tiết một công việc CSKH")
    public ApiResponse<CareTaskView> get(@PathVariable long taskId) {
        return ApiResponse.ok(service.get(taskId));
    }

    @PatchMapping("/tasks/{taskId}/start")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Nhận xử lý: Chờ chăm sóc → Đang xử lý (người bấm trở thành người phụ trách)")
    public ApiResponse<CareTaskView> start(@PathVariable long taskId, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok("Đã nhận xử lý công việc", service.start(taskId, actor));
    }

    @PatchMapping("/tasks/{taskId}/request-medical-support")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Chuyển Cần hỗ trợ chuyên môn: nhờ bác sĩ phụ trách ca đó trả lời (SLA vẫn chạy)")
    public ApiResponse<CareTaskView> requestMedical(@PathVariable long taskId, @AuthenticationPrincipal UserPrincipal actor,
                                                    @Valid @RequestBody MedicalSupportRequest req) {
        return ApiResponse.ok("Đã chuyển yêu cầu hỗ trợ chuyên môn cho bác sĩ", service.requestMedicalSupport(taskId, actor, req.note()));
    }

    @PatchMapping("/tasks/{taskId}/close")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Đóng công việc từ trạng thái Đang xử lý (bắt buộc Result_Note)")
    public ApiResponse<CareTaskView> close(@PathVariable long taskId, @AuthenticationPrincipal UserPrincipal actor,
                                           @Valid @RequestBody CloseTaskRequest req) {
        return ApiResponse.ok("Đã đóng công việc", service.close(taskId, actor, req.resultNote()));
    }

    @PostMapping("/tasks/{taskId}/send-recall")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Gửi tin mời tái khám cho việc Nhắc tái khám (chỉ gửi tin, không tự tạo lịch; khách opt-out bị từ chối)")
    public ApiResponse<CareTaskView> sendRecall(@PathVariable long taskId, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok("Đã xếp hàng gửi tin mời tái khám", service.sendRecallInvitation(taskId, actor));
    }

    @GetMapping("/upcoming-appointments")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Lịch Đã xác nhận sắp đến trong N giờ tới (mặc định 48h) để gọi xác nhận")
    public ApiResponse<List<UpcomingAppointmentView>> upcoming(@RequestParam(defaultValue = "48") int hours) {
        return ApiResponse.ok(queries.upcomingAppointments(Math.min(Math.max(hours, 1), 168)));
    }

    @PostMapping("/upcoming-appointments/{appointmentId}/confirm-call")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Ghi nhận đã gọi xác nhận lịch (tạo công việc CONFIRM_CALL đã đóng để có dấu vết)")
    public ApiResponse<CareTaskView> confirmCall(@PathVariable long appointmentId, @AuthenticationPrincipal UserPrincipal actor,
                                                 @Valid @RequestBody ConfirmCallRequest req) {
        return ApiResponse.ok("Đã ghi nhận cuộc gọi xác nhận", service.recordConfirmCall(appointmentId, actor, req.resultNote()));
    }
}
