package com.chia.dental.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    public static class PatientSummaryDto {
        private Long patientId;
        private String fullName;
        private Short birthYear;
        private String gender;
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
        private Long suggestedDoctorId;
        private String suggestedDoctorName;
        private List<PatientSummaryDto> patients;
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
        private String prototypeOtp; // Chỉ hiển thị ở môi trường prototype/dev để dễ test
    }

    @Data
    public static class ConsentInputDto {
        private Boolean accepted = true;
        private String policyVersion = "v1.0-2026";
        private String channel = "WEB_PORTAL";
        private String consentedBy;
        private String relation = "SELF";
        private Boolean isGuardian = false;
    }

    @Data
    public static class OtpVerifyDto {
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Số điện thoại không hợp lệ")
        private String phone;

        @NotBlank(message = "Mã OTP không được để trống")
        @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải gồm 6 chữ số")
        @JsonAlias("otpCode")
        private String otp;

        @Pattern(regexp = "^(LOGIN|BOOKING)$", message = "purpose phải là LOGIN hoặc BOOKING")
        private String purpose = "BOOKING";

        private ConsentInputDto consent;
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
        private Long expiresInSeconds;
        private Long userId;
        private Long customerId;
        private String username;
        private String phone;
        private String fullName;
        private String role;
        private Boolean isBlacklisted;
    }
}
