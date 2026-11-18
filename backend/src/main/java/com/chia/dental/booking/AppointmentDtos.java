package com.chia.dental.booking;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
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
    public static class PatientInfoDto {
        @NotBlank(message = "Họ tên bệnh nhân không được để trống")
        @Size(max = 100)
        private String fullName;

        @NotNull(message = "Năm sinh không được để trống")
        @Min(value = 1900, message = "Năm sinh không hợp lệ")
        @Max(value = 2100, message = "Năm sinh không hợp lệ")
        private Short birthYear;

        @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Giới tính phải là MALE, FEMALE hoặc OTHER")
        private String gender;

        @Size(max = 255)
        private String address;

        @Size(max = 30)
        private String relation;

        private Boolean isGuardian = false;
    }

    @Data
    public static class CreateAppointmentRequest {
        @Pattern(regexp = "^(SELF|RELATIVE)$", message = "bookingFor phải là SELF hoặc RELATIVE")
        private String bookingFor = "SELF";

        private Long patientId; // Nếu null -> tạo hồ sơ bệnh nhân mới theo patientInfo hoặc patientFullName/birthYear

        @Valid
        private PatientInfoDto patientInfo;

        // Giữ tương thích ngược 6.3
        private String patientFullName;

        @Min(value = 1900, message = "Năm sinh không hợp lệ")
        @Max(value = 2100, message = "Năm sinh không hợp lệ")
        private Short birthYear;

        @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Giới tính phải là MALE, FEMALE hoặc OTHER")
        private String gender;

        // Tùy chọn: nếu null -> hệ thống tự động phân bổ bác sĩ ít lịch nhất (5.3.8 mục 1)
        @JsonAlias("doctorId")
        private Long staffId;

        private Long chairId; // Tùy chọn: nếu null hệ thống tự chọn ghế trống

        @NotNull(message = "Vui lòng chọn ngày giờ hẹn")
        private OffsetDateTime dateTime;

        @NotEmpty(message = "Vui lòng chọn ít nhất một dịch vụ")
        private List<Long> serviceIds;

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        private String note;
    }

    @Data
    public static class TemporaryServiceDto {
        @NotNull(message = "Vui lòng nhập thời lượng dịch vụ tạm (phút)")
        @Min(value = 5, message = "Thời lượng dịch vụ tạm tối thiểu 5 phút")
        @Max(value = 240, message = "Thời lượng dịch vụ tạm tối đa 240 phút")
        private Short durationMinutes;

        @Size(max = 200)
        private String description;
    }

    @Data
    public static class StaffCreateAppointmentRequest {
        @NotBlank(message = "Vui lòng chọn nguồn đặt lịch (PHONE, SOCIAL, WALK_IN)")
        @Pattern(regexp = "^(PHONE|SOCIAL|WALK_IN)$", message = "source phải là PHONE, SOCIAL hoặc WALK_IN")
        private String source;

        @NotBlank(message = "Số điện thoại khách hàng không được để trống")
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Số điện thoại không hợp lệ")
        private String phone;

        private Long patientId; // Nếu null -> tạo mới bằng patientInfo

        @Valid
        private PatientInfoDto patientInfo;

        @JsonAlias("doctorId")
        private Long staffId; // Tùy chọn: nếu null tự động chọn bác sĩ rảnh

        private Long chairId; // Tùy chọn: nếu null tự động chọn ghế trống

        private OffsetDateTime dateTime; // Tùy chọn với WALK_IN (mặc định now), bắt buộc với PHONE/SOCIAL

        private List<Long> serviceIds; // Tùy chọn nếu có temporaryService (WALK_IN)

        @Valid
        private TemporaryServiceDto temporaryService; // Dịch vụ tạm thời (is_temporary = true, service_id = null)

        private Boolean isPriority = false; // Đánh dấu ca cấp cứu ưu tiên

        @Size(max = 500)
        private String note;
    }

    @Data
    public static class CustomerRescheduleRequest {
        @NotNull(message = "Vui lòng chọn ngày giờ hẹn mới")
        private OffsetDateTime dateTime;

        @JsonAlias("doctorId")
        private Long staffId; // Tùy chọn: đổi bác sĩ hoặc để trống giữ nguyên/tự phân bổ

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        private String note;
    }

    @Data
    public static class StaffRescheduleRequest {
        @NotNull(message = "Vui lòng chọn ngày giờ hẹn mới")
        private OffsetDateTime dateTime;

        @JsonAlias("doctorId")
        private Long staffId;

        private Long chairId;

        @Size(max = 500)
        private String note;
    }

    @Data
    public static class ConfirmAppointmentRequest {
        @JsonAlias("doctorId")
        private Long staffId; // Tùy chọn chốt/đổi bác sĩ khi duyệt
        private Long chairId; // Tùy chọn chốt/đổi ghế khi duyệt
    }

    @Data
    public static class CancelAppointmentRequest {
        @NotBlank(message = "Vui lòng nhập lý do hủy lịch")
        @Size(max = 255)
        private String cancelReason;
    }

    @Data
    public static class ReverseNoShowRequest {
        @NotBlank(message = "Bắt buộc nhập lý do hoàn tác trạng thái Không đến")
        @Size(max = 255)
        private String reversedReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServiceItemDto {
        private Long serviceId;
        private String serviceName;
        private Boolean isTemporary;
        private Short durationSnapshot;
        private BigDecimal priceSnapshot;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppointmentResponse {
        private Long appointmentId;
        private String appointmentCode;
        private Long patientId;
        private String patientName;
        private String bookedByPhone;
        private Long doctorId;
        private String doctorName;
        private Boolean doctorAutoAssigned;
        private Long chairId;
        private String chairName;
        private OffsetDateTime dateTime;
        private Short duration;
        private OffsetDateTime endTime;
        private String status;
        private String statusLabel;
        private String source;
        private OffsetDateTime holdUntil;
        private OffsetDateTime actualArrivalAt;
        private Boolean canModify;
        private Boolean isPriority;
        private Boolean hasLeaveConflict;
        private Boolean customerBlacklisted;
        private String cancelReason;
        private String note;
        private OffsetDateTime createdAt;
        private List<ServiceItemDto> services;
    }
}
