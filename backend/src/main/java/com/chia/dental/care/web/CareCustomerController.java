package com.chia.dental.care.web;

import com.chia.dental.care.CareDtos.*;
import com.chia.dental.care.CareQueryRepository;
import com.chia.dental.care.OptOutService;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.common.response.ApiResponse;
import com.chia.dental.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/staff/care")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER')")
@Tag(name = "8. CSKH - Khách hàng, opt-out & nhật ký gửi tin",
        description = "Tra cứu khách hàng phần CSKH (không có hồ sơ y tế), quyền từ chối nhận tin, nhật ký gửi tin Zalo/SMS. Bác sĩ không truy cập")
public class CareCustomerController {

    private final CareQueryRepository queries;
    private final OptOutService optOutService;

    @GetMapping("/customers")
    @Operation(summary = "Tìm khách hàng theo SĐT hoặc tên bệnh nhân")
    public ApiResponse<List<CustomerSummary>> search(@RequestParam(required = false) String q) {
        return ApiResponse.ok(queries.searchCustomers(q));
    }

    @GetMapping("/customers/{customerId}")
    @Operation(summary = "Hồ sơ CSKH của khách: bệnh nhân, thống kê lịch, no-show, opt-out, lịch sử khám, công việc, tin đã gửi, đánh giá")
    public ApiResponse<CustomerCareOverview> overview(@PathVariable long customerId) {
        return ApiResponse.ok(queries.customerOverview(customerId).orElseThrow(() ->
                new BusinessException("CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng.", HttpStatus.NOT_FOUND)));
    }

    @PutMapping("/customers/{customerId}/opt-out")
    @Operation(summary = "Bật/tắt Từ chối nhận tin CSKH (chặn nhắc lịch, hỏi thăm, tái khám; không chặn OTP và xác nhận/đổi/hủy lịch)")
    public ApiResponse<Void> optOut(@PathVariable long customerId, @AuthenticationPrincipal UserPrincipal actor,
                                    @Valid @RequestBody OptOutRequest req) {
        optOutService.set(customerId, actor, req);
        return ApiResponse.ok(Boolean.TRUE.equals(req.optedOut())
                ? "Đã ghi nhận khách từ chối nhận tin CSKH" : "Đã bỏ từ chối nhận tin", null);
    }

    @GetMapping("/notification-logs")
    @Operation(summary = "Nhật ký gửi tin (mỗi lần gửi/chuyển kênh một dòng), lọc theo khách/trạng thái/loại/thời gian")
    public ApiResponse<PageResult<NotificationLogView>> logs(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(queries.notificationLogs(customerId, blankToNull(status), blankToNull(type), from, to,
                Math.max(0, page), Math.min(Math.max(size, 1), 100)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }
}
