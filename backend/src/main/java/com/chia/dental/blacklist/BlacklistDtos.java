package com.chia.dental.blacklist;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

public class BlacklistDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NoShowEventDto {
        private Long eventId;
        private Long appointmentId;
        private Long patientId;
        private String patientName;
        private OffsetDateTime occurredAt;
        private Boolean isReversed;
        private String reversedByName;
        private OffsetDateTime reversedAt;
        private String reversedReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlacklistedCustomerDto {
        private Long customerId;
        private String phone;
        private Boolean isBlacklisted;
        private long activeNoShowCountInWindow;
        private int threshold;
        private boolean stillExceedsThreshold;
        private List<String> patientNames;
        private List<NoShowEventDto> noShowHistory;
    }

    @Data
    public static class UnlockBlacklistRequest {
        @NotBlank(message = "Bắt buộc nhập lý do gỡ khóa Blacklist")
        @Size(max = 255)
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UnlockBlacklistResponse {
        private Long customerId;
        private String phone;
        private Boolean isBlacklisted;
        private long activeNoShowCountInWindow;
        private String warning;
        private String reason;
        private Long unlockedByStaffId;
        private OffsetDateTime unlockedAt;
    }
}

