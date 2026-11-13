package com.chia.dental.auth;

import com.chia.dental.auth.AuthDtos.*;
import com.chia.dental.common.response.ApiResponse;
import com.chia.dental.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication & OTP", description = "Xác thực nhân sự (JWT) và xác thực khách hàng (OTP giả lập)")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/customer/check-phone")
    @Operation(summary = "Kiểm tra SĐT và trạng thái Blacklist trước khi đặt lịch")
    public ApiResponse<CheckPhoneResponse> checkPhone(@Valid @RequestBody CheckPhoneRequest req) {
        return ApiResponse.ok(authService.checkPhone(req.getPhone()));
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Yêu cầu gửi mã OTP giả lập (trả về prototypeOtp để test)")
    public ApiResponse<OtpRequestResponse> requestOtp(@Valid @RequestBody OtpRequestDto req) {
        return ApiResponse.ok("Đã sinh mã OTP giả lập", authService.requestOtp(req));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Xác thực mã OTP và cấp Customer JWT Token")
    public ApiResponse<AuthTokenResponse> verifyOtp(@Valid @RequestBody OtpVerifyDto req) {
        return ApiResponse.ok("Xác thực OTP thành công", authService.verifyOtp(req));
    }

    @PostMapping("/staff/login")
    @Operation(summary = "Đăng nhập nội bộ (Lễ tân, Bác sĩ, Quản lý) - tài khoản mẫu mật khẩu 123456")
    public ApiResponse<AuthTokenResponse> staffLogin(@Valid @RequestBody StaffLoginRequest req) {
        return ApiResponse.ok("Đăng nhập thành công", authService.staffLogin(req));
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin phiên đăng nhập hiện tại từ JWT")
    public ApiResponse<UserPrincipal> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(authService.getCurrentUser(principal));
    }
}

