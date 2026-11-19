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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/manager/care")
@RequiredArgsConstructor
@Tag(name = "11. CSKH - Quản lý (Q5)",
        description = "Chỉ Quản lý: ẩn/hiện phản hồi và khiếu nại (bắt buộc lý do, không xóa vật lý, ghi audit), báo cáo CSKH")
public class ManagerCareController {

    private final FeedbackService feedbackService;
    private final ComplaintService complaintService;
    private final CareQueryRepository queries;

    @GetMapping("/reports/summary")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Báo cáo CSKH: việc theo trạng thái/loại, quá hạn SLA, tỷ lệ gửi tin thành công, điểm đánh giá, khiếu nại")
    public ApiResponse<CareReportSummary> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ApiResponse.ok(queries.report(from, to));
    }

    @PatchMapping("/feedback/{feedbackId}/hide")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Ẩn phản hồi (bắt buộc lý do, ghi audit, không xóa)")
    public ApiResponse<Void> hideFeedback(@PathVariable long feedbackId, @AuthenticationPrincipal UserPrincipal actor,
                                          @Valid @RequestBody HideRequest req) {
        feedbackService.hide(feedbackId, actor, req.reason(), true);
        return ApiResponse.ok("Đã ẩn phản hồi", null);
    }

    @PatchMapping("/feedback/{feedbackId}/unhide")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Hiển thị lại phản hồi đã ẩn (bắt buộc lý do, ghi audit)")
    public ApiResponse<Void> unhideFeedback(@PathVariable long feedbackId, @AuthenticationPrincipal UserPrincipal actor,
                                            @Valid @RequestBody HideRequest req) {
        feedbackService.hide(feedbackId, actor, req.reason(), false);
        return ApiResponse.ok("Đã hiển thị lại phản hồi", null);
    }

    @PatchMapping("/complaints/{id}/hide")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Ẩn khiếu nại (bắt buộc lý do, ghi audit, không xóa)")
    public ApiResponse<Void> hideComplaint(@AuthenticationPrincipal UserPrincipal actor, @PathVariable long id,
                                           @Valid @RequestBody HideRequest req) {
        complaintService.hide(actor, id, req.reason(), true);
        return ApiResponse.ok("Đã ẩn khiếu nại", null);
    }

    @PatchMapping("/complaints/{id}/unhide")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Hiển thị lại khiếu nại đã ẩn (bắt buộc lý do, ghi audit)")
    public ApiResponse<Void> unhideComplaint(@AuthenticationPrincipal UserPrincipal actor, @PathVariable long id,
                                             @Valid @RequestBody HideRequest req) {
        complaintService.hide(actor, id, req.reason(), false);
        return ApiResponse.ok("Đã hiển thị lại khiếu nại", null);
    }
}
