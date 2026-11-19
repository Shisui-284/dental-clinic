package com.chia.dental.care;

import com.chia.dental.care.CareDtos.OptOutRequest;
import com.chia.dental.care.notification.OptOutPolicy;
import com.chia.dental.common.audit.AuditLogService;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.OptOut;
import com.chia.dental.repository.CustomerRepository;
import com.chia.dental.repository.OptOutRepository;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bật/tắt "Từ chối nhận tin" cho khách (SRS 2.2.9, FR-29). Chỉ lễ tân/quản lý thao tác; ghi audit. */
@Service
@RequiredArgsConstructor
public class OptOutService {

    private static final Set<String> CHANNELS = Set.of(CareConstants.CH_ALL, CareConstants.CH_ZALO, CareConstants.CH_SMS);

    private final OptOutRepository repository;
    private final CustomerRepository customerRepository;
    private final AuditLogService audit;

    @Transactional
    public void set(long customerId, UserPrincipal actor, OptOutRequest req) {
        if (!customerRepository.existsById(customerId)) {
            throw new BusinessException("CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng.", HttpStatus.NOT_FOUND);
        }
        String channel = req.channel() == null || req.channel().isBlank() ? CareConstants.CH_ALL : req.channel().trim().toUpperCase();
        if (!CHANNELS.contains(channel)) {
            throw new BusinessException("INVALID_CHANNEL", "Kênh phải là ALL, ZALO_ZNS hoặc SMS.", HttpStatus.BAD_REQUEST);
        }
        var existing = repository.findFirstByCustomerIdAndChannelAndIsActiveTrue(customerId, channel);
        if (Boolean.TRUE.equals(req.optedOut())) {
            if (existing.isPresent()) {
                throw new BusinessException("ALREADY_OPTED_OUT", "Khách đã ở trạng thái từ chối nhận tin trên kênh này.", HttpStatus.CONFLICT);
            }
            repository.save(OptOut.builder().customerId(customerId).channel(channel)
                    .excludedNotifTypes(OptOutPolicy.DEFAULT_EXCLUDED).reason(req.reason())
                    .isActive(true).createdAt(OffsetDateTime.now(CareConstants.ZONE)).createdBy(actor.getId()).build());
            audit.logStaff(actor.getId(), "OPT_OUT_ON", "customer", customerId, null,
                    Map.of("channel", channel, "reason", String.valueOf(req.reason())));
        } else {
            OptOut o = existing.orElseThrow(() ->
                    new BusinessException("NOT_OPTED_OUT", "Khách chưa từ chối nhận tin trên kênh này.", HttpStatus.CONFLICT));
            o.setIsActive(false);
            repository.save(o);
            audit.logStaff(actor.getId(), "OPT_OUT_OFF", "customer", customerId, Map.of("channel", channel), null);
        }
    }

    @Transactional(readOnly = true)
    public List<OptOut> active(long customerId) {
        return repository.findByCustomerIdAndIsActiveTrue(customerId);
    }
}
