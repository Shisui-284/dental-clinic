package com.chia.dental.booking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class PublicDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceDto {
        private Long serviceId;
        private String name;
        private Short duration;
        private BigDecimal price;
        private Integer followupCycleDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoctorDto {
        private Long staffId;
        private String username;
        private String fullName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SlotDto {
        private String startTime; // HH:mm
        private String endTime;   // HH:mm
        private String period;    // MORNING, AFTERNOON, EVENING
        private boolean available;
        private Long doctorId;
        private String doctorName;
        private Long availableChairId; // Chỉ hiển thị cho nhân sự (ẩn với khách hàng theo 5.3.5)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DaySlotsDto {
        private LocalDate date;
        @JsonProperty("isHoliday")
        private boolean isHoliday;
        private String holidayName;
        private List<SlotDto> slots;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AvailableSlotsResponse {
        // Các trường chuẩn 5.3.5 (Mục 1)
        private int serviceDurationMinutes;
        private int chairBufferMinutes;
        private int newPatientBufferMinutes;
        private int totalCalculatedDuration;
        @JsonProperty("isNewPatient")
        private boolean isNewPatient;
        private List<DaySlotsDto> days;

        // Các trường tương thích ngược 6.3 cho truy vấn đơn ngày
        private LocalDate date;
        private int totalDurationMinutes;
        private boolean holiday;
        private String holidayName;
        private List<SlotDto> slots;
    }
}
