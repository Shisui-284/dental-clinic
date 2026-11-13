package com.chia.dental.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDtos {

    @Data
    public static class CheckPhoneRequest {
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Số điện thoại không hợp lệ (9-15 chữ số)")
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckPhoneResponse {
        private String phone;
        private boolean existingCustomer;
        private boolean blacklisted;
        private String message;
    }

    @Data
    public static class OtpRequestDto {
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Số điện thoại không hợp lệ")
        private String phone;

        @Pattern(regexp = "^(LOGIN|BOOKING)$", message = "purpose phải là LOGIN hoặc BOOKING")
        private String purpose = "BOOKING";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpRequestResponse {
        private String phone;
        private String purpose;
        private int expireMinutes;
        private String prototypeOtp; // Chỉ hiển thị ở môi trường prototype để dễ test
    }

    @Data
    public static class OtpVerifyDto {
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Số điện thoại không hợp lệ")
        private String phone;

        @NotBlank(message = "Mã OTP không được để trống")
        @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải gồm 6 chữ số")
        private String otp;

        @Pattern(regexp = "^(LOGIN|BOOKING)$", message = "purpose phải là LOGIN hoặc BOOKING")
        private String purpose = "BOOKING";
    }

    @Data
    public static class StaffLoginRequest {
        @NotBlank(message = "Tên đăng nhập không được để trống")
        private String username;

        @NotBlank(message = "Mật khẩu không được để trống")
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthTokenResponse {
        private String accessToken;
        private String tokenType;
        private Long userId;
        private String username;
        private String fullName;
        private String role;
    }
}

