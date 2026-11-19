package com.chia.dental.care.web;

import com.chia.dental.care.CareDtos.*;
import com.chia.dental.care.CareQueryRepository;
import com.chia.dental.care.complaint.ComplaintService;
import com.chia.dental.care.feedback.FeedbackService;
import com.chia.dental.common.response.ApiResponse;
import com.chia.dental.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/staff/care")
@RequiredArgsConstructor
@Tag(name = "9. CSKH - Phản hồi, khiếu nại & dashboard",
        description = "Đánh giá của khách, cảnh báo đỏ, xử lý khiếu nại, dashboard. Bác sĩ chỉ xem phản hồi/khiếu nại của ca mình trực tiếp khám")
public class FeedbackComplaintController {

    private final FeedbackService feedbackService;
    private final ComplaintService complaintService;
    private final CareQueryRepository queries;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Dashboard CSKH: số việc theo trạng thái, quá hạn, khiếu nại, cảnh báo đỏ, tin gửi lỗi 24h")
    public ApiResponse<DashboardView> dashboard() {
        return ApiResponse.ok(queries.dashboard());
    }

    @GetMapping("/feedback")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','DOCTOR')")
    @Operation(summary = "Danh sách phản hồi (bác sĩ chỉ thấy ca mình khám; phản hồi đã ẩn chỉ Quản lý thấy khi includeHidden)")
    public ApiResponse<PageResult<FeedbackView>> feedback(@AuthenticationPrincipal UserPrincipal actor,
                                                          @RequestParam(defaultValue = "false") boolean alertOnly,
                                                          @RequestParam(defaultValue = "false") boolean includeHidden,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(feedbackService.list(actor, alertOnly, includeHidden, page, size));
    }

    @GetMapping("/complaints")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','DOCTOR')")
    @Operation(summary = "Danh sách khiếu nại (bác sĩ chỉ thấy khiếu nại gắn phản hồi ca mình khám, ẩn SĐT)")
    public ApiResponse<PageResult<ComplaintView>> complaints(@AuthenticationPrincipal UserPrincipal actor,
                                                             @RequestParam(required = false) String status,
                                                             @RequestParam(defaultValue = "false") boolean includeHidden,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(complaintService.list(actor, status, includeHidden, page, size));
    }

    @GetMapping("/complaints/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','DOCTOR')")
    @Operation(summary = "Chi tiết khiếu nại")
    public ApiResponse<ComplaintView> complaint(@AuthenticationPrincipal UserPrincipal actor, @PathVariable long id) {
        return ApiResponse.ok(complaintService.get(actor, id));
    }

    @PostMapping("/complaints")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Ghi nhận khiếu nại khách phản ánh trực tiếp (giao lễ tân vòng tròn, hạn 48h làm việc)")
    public ApiResponse<ComplaintView> create(@AuthenticationPrincipal UserPrincipal actor,
                                             @Valid @RequestBody CreateComplaintRequest req) {
        return ApiResponse.ok("Đã ghi nhận khiếu nại", complaintService.create(actor, req));
    }

    @PatchMapping("/complaints/{id}/status")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
    @Operation(summary = "Chuyển trạng thái khiếu nại: Mở → Đang xử lý → Chờ phản hồi → Đóng (đóng bắt buộc kết quả và biện pháp)")
    public ApiResponse<ComplaintView> changeStatus(@AuthenticationPrincipal UserPrincipal actor, @PathVariable long id,
                                                   @Valid @RequestBody ComplaintStatusRequest req) {
        return ApiResponse.ok("Đã cập nhật khiếu nại", complaintService.changeStatus(actor, id, req));
    }
}
