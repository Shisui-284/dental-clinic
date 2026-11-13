package com.chia.dental.booking;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class AppointmentDtos {

    @Data
    public static class CreateAppointmentRequest {
        private Long patientId; // Nếu null -> tạo hồ sơ bệnh nhân mới theo thông tin bên dưới

        private String patientFullName;

        @Min(value = 1900, message = "Năm sinh không hợp lệ")
        @Max(value = 2100, message = "Năm sinh không hợp lệ")
        private Short birthYear;

        @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Giới tính phải là MALE, FEMALE hoặc OTHER")
        private String gender;

        @NotNull(message = "Vui lòng chọn bác sĩ")
        private Long doctorId;

        private Long chairId; // Tùy chọn: nếu null hệ thống tự chọn ghế trống

        @NotNull(message = "Vui lòng chọn ngày giờ hẹn")
        private OffsetDateTime dateTime;

        @NotEmpty(message = "Vui lòng chọn ít nhất một dịch vụ")
        private List<Long> serviceIds;

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        private String note;
    }

    @Data
    public static class ConfirmAppointmentRequest {
        private Long doctorId; // Tùy chọn đổi bác sĩ khi duyệt
        private Long chairId;  // Tùy chọn đổi ghế khi duyệt
    }

    @Data
    public static class CancelAppointmentRequest {
        @NotBlank(message = "Vui lòng nhập lý do hủy lịch")
        @Size(max = 255)
        private String cancelReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceItemDto {
        private Long serviceId;
        private String serviceName;
        private Short durationSnapshot;
        private BigDecimal priceSnapshot;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppointmentResponse {
        private Long appointmentId;
        private Long patientId;
        private String patientName;
        private String bookedByPhone;
        private Long doctorId;
        private String doctorName;
        private Long chairId;
        private String chairName;
        private OffsetDateTime dateTime;
        private Short duration;
        private OffsetDateTime endTime;
        private String status;
        private String source;
        private OffsetDateTime holdUntil;
        private String note;
        private OffsetDateTime createdAt;
        private List<ServiceItemDto> services;
    }
}

