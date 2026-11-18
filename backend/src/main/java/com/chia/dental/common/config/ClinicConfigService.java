package com.chia.dental.common.config;

import com.chia.dental.repository.ClinicConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicConfigService {

    private final ClinicConfigRepository clinicConfigRepository;

    public LocalTime getClinicOpenTime() {
        return getTimeConfig("clinic_open_time", LocalTime.of(8, 0));
    }

    public LocalTime getClinicCloseTime() {
        return getTimeConfig("clinic_close_time", LocalTime.of(18, 0));
    }

    public int getChairBufferMinutes() {
        return getIntConfig("chair_buffer_minutes", 5);
    }

    public int getNewPatientBufferMinutes() {
        return getIntConfig("new_patient_buffer_minutes", 15);
    }

    public int getBookingMinLeadHours() {
        return getIntConfig("booking_min_lead_hours", 2);
    }

    public int getBookingMaxLeadDays() {
        return getIntConfig("booking_max_lead_days", 30);
    }

    public int getCustomerChangeMinHours() {
        return getIntConfig("customer_change_min_hours", 4);
    }

    public int getLateNoshowMinutes() {
        return getIntConfig("late_noshow_minutes", 15);
    }

    public int getBlacklistThreshold() {
        return getIntConfig("blacklist_threshold", 3);
    }

    public int getBlacklistWindowMonths() {
        return getIntConfig("blacklist_window_months", 12);
    }

    public int getIntConfig(String key, int defaultValue) {
        return clinicConfigRepository.findRawTextValueByKey(key)
                .map(raw -> {
                    try {
                        return Integer.parseInt(raw.trim());
                    } catch (NumberFormatException ex) {
                        log.warn("Giá trị cấu hình '{}' không hợp lệ ({}), dùng mặc định {}", key, raw, defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    public LocalTime getTimeConfig(String key, LocalTime defaultValue) {
        return clinicConfigRepository.findRawTextValueByKey(key)
                .map(raw -> {
                    try {
                        return LocalTime.parse(raw.trim());
                    } catch (Exception ex) {
                        log.warn("Giá trị giờ '{}' không hợp lệ ({}), dùng mặc định {}", key, raw, defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}

