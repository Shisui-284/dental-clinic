package com.chia.dental.blacklist;

import com.chia.dental.blacklist.BlacklistDtos.*;
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
@RequestMapping("/api/v1/manager/blacklist")
@RequiredArgsConstructor
@Tag(name = "6. Manager Blacklist (Q2)", description = "Quản lý danh sách khách hàng hạn chế đặt lịch trực tuyến (No-show >= 3 lần/12 tháng)")
public class ManagerBlacklistController {

    private final BlacklistService blacklistService;

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Xem danh sách khách hàng đang bị khóa Blacklist kèm lịch sử no_show_event")
    public ApiResponse<List<BlacklistedCustomerDto>> listBlacklistedCustomers() {
        return ApiResponse.ok(blacklistService.getBlacklistedCustomers());
    }

    @PostMapping("/{customerId}/unlock")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Quản lý gỡ khóa Blacklist thủ công (không xóa no_show_event cũ, bắt buộc lý do & ghi Audit Log)")
    public ApiResponse<UnlockBlacklistResponse> unlockBlacklist(
            @PathVariable Long customerId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UnlockBlacklistRequest req) {
        return ApiResponse.ok("Đã gỡ khóa Blacklist cho khách hàng thành công",
                blacklistService.unlockBlacklist(customerId, principal, req));
    }
}

