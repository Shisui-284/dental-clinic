package com.chia.dental.booking;

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
    public static class SlotDto {
        private String startTime; // HH:mm
        private String endTime;   // HH:mm
        private Long doctorId;
        private String doctorName;
        private Long availableChairId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailableSlotsResponse {
        private LocalDate date;
        private int totalDurationMinutes;
        private boolean holiday;
        private String holidayName;
        private List<SlotDto> slots;
    }
}

