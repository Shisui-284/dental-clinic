package com.chia.dental.blacklist;

import com.chia.dental.blacklist.BlacklistDtos.*;
import com.chia.dental.common.audit.AuditLogService;
import com.chia.dental.common.config.ClinicConfigService;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.Customer;
import com.chia.dental.entity.NoShowEvent;
import com.chia.dental.entity.PatientProfile;
import com.chia.dental.repository.CustomerRepository;
import com.chia.dental.repository.NoShowEventRepository;
import com.chia.dental.repository.PatientProfileRepository;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BlacklistService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CustomerRepository customerRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final NoShowEventRepository noShowEventRepository;
    private final ClinicConfigService clinicConfigService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<BlacklistedCustomerDto> getBlacklistedCustomers() {
        int threshold = clinicConfigService.getBlacklistThreshold();
        int windowMonths = clinicConfigService.getBlacklistWindowMonths();
        OffsetDateTime windowStart = OffsetDateTime.now(CLINIC_ZONE).minusMonths(windowMonths);

        return customerRepository.findByIsBlacklistedTrueOrderByCustomerIdAsc().stream()
                .map(c -> {
                    List<String> patientNames = patientProfileRepository.findByCustomerCustomerId(c.getCustomerId())
                            .stream()
                            .map(PatientProfile::getFullName)
                            .toList();
                    List<NoShowEventDto> history = noShowEventRepository.findByCustomerIdWithDetails(c.getCustomerId())
                            .stream()
                            .map(this::toDto)
                            .toList();
                    long activeCount = noShowEventRepository.countActiveInWindow(c.getCustomerId(), windowStart);

                    return BlacklistedCustomerDto.builder()
                            .customerId(c.getCustomerId())
                            .phone(c.getPhone())
                            .isBlacklisted(c.getIsBlacklisted())
                            .activeNoShowCountInWindow(activeCount)
                            .threshold(threshold)
                            .stillExceedsThreshold(activeCount >= threshold)
                            .patientNames(patientNames)
                            .noShowHistory(history)
                            .build();
                })
                .toList();
    }

    /**
     * Gỡ khóa Blacklist đặc cách của Quản lý (Màn Q2 - 5.3.8 mục 3):
     * - KHÔNG xóa hay chỉnh sửa các bản ghi no_show_event cũ (bảo toàn thống kê FR-36).
     * - Cập nhật trực tiếp customer.is_blacklisted = false và ghi audit_log bắt buộc.
     */
    @Transactional
    public UnlockBlacklistResponse unlockBlacklist(Long customerId, UserPrincipal principal, UnlockBlacklistRequest req) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND",
                        "Không tìm thấy khách hàng #" + customerId,
                        HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(customer.getIsBlacklisted())) {
            throw new BusinessException("CUSTOMER_NOT_BLACKLISTED",
                    "Khách hàng hiện không nằm trong danh sách hạn chế Blacklist.",
                    HttpStatus.BAD_REQUEST);
        }

        int threshold = clinicConfigService.getBlacklistThreshold();
        int windowMonths = clinicConfigService.getBlacklistWindowMonths();
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        long activeCount = noShowEventRepository.countActiveInWindow(customerId, now.minusMonths(windowMonths));

        customer.setIsBlacklisted(false);
        customerRepository.saveAndFlush(customer);

        String warning = activeCount >= threshold
                ? String.format("Lưu ý: Khách hàng vẫn còn %d lần không đến trong %d tháng gần nhất (dữ liệu no_show_event được giữ nguyên cho báo cáo).",
                        activeCount, windowMonths)
                : null;

        Map<String, Object> newAudit = new LinkedHashMap<>();
        newAudit.put("isBlacklisted", false);
        newAudit.put("reason", req.getReason().trim());
        newAudit.put("unlockedAt", now.toString());
        newAudit.put("activeNoShowCountInWindow", activeCount);

        auditLogService.logStaff(
                principal.getId(),
                "BLACKLIST_UNLOCK",
                "customer",
                customer.getCustomerId(),
                Map.of("isBlacklisted", true),
                newAudit
        );

        return UnlockBlacklistResponse.builder()
                .customerId(customer.getCustomerId())
                .phone(customer.getPhone())
                .isBlacklisted(false)
                .activeNoShowCountInWindow(activeCount)
                .warning(warning)
                .reason(req.getReason().trim())
                .unlockedByStaffId(principal.getId())
                .unlockedAt(now)
                .build();
    }

    private NoShowEventDto toDto(NoShowEvent e) {
        return NoShowEventDto.builder()
                .eventId(e.getEventId())
                .appointmentId(e.getAppointment().getAppointmentId())
                .patientId(e.getPatient().getPatientId())
                .patientName(e.getPatient().getFullName())
                .occurredAt(e.getOccurredAt())
                .isReversed(e.getIsReversed())
                .reversedByName(e.getReversedBy() != null ? e.getReversedBy().getFullName() : null)
                .reversedAt(e.getReversedAt())
                .reversedReason(e.getReversedReason())
                .build();
    }
}

