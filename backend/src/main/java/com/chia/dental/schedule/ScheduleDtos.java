package com.chia.dental.schedule;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public class ScheduleDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleDto {
        private Long scheduleId;
        private Long staffId;
        private String doctorName;
        private LocalDate shiftDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String status;
    }

    @Data
    public static class CreateScheduleRequest {
        @NotNull(message = "Vui lòng chọn bác sĩ")
        @JsonAlias("doctorId")
        private Long staffId;

        @NotNull(message = "Vui lòng chọn ngày làm việc")
        private LocalDate shiftDate;

        @NotNull(message = "Vui lòng nhập giờ bắt đầu ca")
        private LocalTime startTime;

        @NotNull(message = "Vui lòng nhập giờ kết thúc ca")
        private LocalTime endTime;
    }

    @Data
    public static class DoctorLeaveRequest {
        @JsonAlias("doctorId")
        private Long staffId; // Bỏ trống khi chính Bác sĩ gửi đơn (lấy từ JWT)

        @NotNull(message = "Vui lòng chọn ngày nghỉ")
        private LocalDate leaveDate;

        @NotNull(message = "Vui lòng nhập giờ bắt đầu nghỉ")
        private LocalTime startTime;

        @NotNull(message = "Vui lòng nhập giờ kết thúc nghỉ")
        private LocalTime endTime;

        @NotBlank(message = "Vui lòng nhập lý do xin nghỉ")
        @Size(max = 255)
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DoctorLeaveDto {
        private Long leaveId;
        private Long staffId;
        private String doctorName;
        private LocalDate leaveDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String reason;
        private String status;
        private Long approvedById;
        private String approvedByName;
        private OffsetDateTime approvedAt;
    }

    @Data
    public static class LeaveApprovalRequest {
        @NotBlank(message = "Vui lòng chọn quyết định APPROVED hoặc REJECTED")
        @Pattern(regexp = "^(APPROVED|REJECTED)$", message = "decision phải là APPROVED hoặc REJECTED")
        @JsonAlias("status")
        private String decision;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaveConflictDto {
        private Long appointmentId;
        private String appointmentCode;
        private Long patientId;
        private String patientName;
        private String bookedByPhone;
        private Long staffId;
        private String doctorName;
        private OffsetDateTime dateTime;
        private OffsetDateTime endTime;
        private Long leaveId;
        private LocalDate leaveDate;
        private LocalTime leaveStartTime;
        private LocalTime leaveEndTime;
        private String leaveReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChairDto {
        private Long chairId;
        private String name;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaveApprovalResponse {
        private DoctorLeaveDto leave;
        private int conflictCount;
        private List<LeaveConflictDto> affectedAppointments;
    }
}

