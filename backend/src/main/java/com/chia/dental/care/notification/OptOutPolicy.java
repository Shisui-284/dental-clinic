package com.chia.dental.care.notification;

import com.chia.dental.care.CareConstants;
import com.chia.dental.entity.OptOut;
import com.chia.dental.repository.OptOutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Quyền từ chối nhận tin (SRS 2.2.9, FR-29). Khách opt-out chỉ nhận OTP và tin xác nhận/đổi/hủy lịch;
 * tuyệt đối không nhận nhắc lịch, nhắc tái khám, hỏi thăm, chúc mừng sinh nhật.
 * Tin giao dịch (NotificationType.careMessage = false) không bao giờ bị chặn.
 */
@Component
@RequiredArgsConstructor
public class OptOutPolicy {

    /** Các loại tin CSKH bị chặn khi tick "Từ chối nhận tin" — đúng danh sách nêu ở SRS 2.2.9. */
    public static final List<String> DEFAULT_EXCLUDED = List.of(
            NotificationType.APPOINTMENT_REMINDER.name(),
            NotificationType.POST_VISIT_CHECK.name(),
            NotificationType.RECALL.name(),
            NotificationType.BIRTHDAY.name());

    private final OptOutRepository repository;

    /** Khách có bị chặn nhận loại tin này trên kênh {@code channel} không. */
    public boolean isBlocked(Long customerId, NotificationType type, String channel) {
        if (!type.isCareMessage()) return false;
        for (OptOut o : repository.findByCustomerIdAndIsActiveTrue(customerId)) {
            boolean channelMatch = CareConstants.CH_ALL.equals(o.getChannel()) || o.getChannel().equals(channel);
            if (channelMatch && o.getExcludedNotifTypes() != null
                    && o.getExcludedNotifTypes().contains(type.name())) {
                return true;
            }
        }
        return false;
    }

    /** Bị chặn trên mọi kênh (dùng để từ chối sớm khi lễ tân bấm "gửi tin mời tái khám"). */
    public boolean isBlockedEverywhere(Long customerId, NotificationType type) {
        return isBlocked(customerId, type, CareConstants.CH_ZALO) && isBlocked(customerId, type, CareConstants.CH_SMS);
    }
}
