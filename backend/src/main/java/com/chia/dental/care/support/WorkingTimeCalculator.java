package com.chia.dental.care.support;

import com.chia.dental.care.CareConstants;
import com.chia.dental.repository.ClinicHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tính mốc thời gian theo GIỜ LÀM VIỆC của phòng khám (quyết định chốt: SLA 48h chỉ tính trong giờ làm việc;
 * SRS 4.2.7 để ngỏ). Giờ mở/đóng: clinic_open_time / clinic_close_time (mặc định 08:00–18:00);
 * ngày làm việc: clinic_working_days (ISO 1=T2..7=CN, mặc định T2–T7); loại trừ clinic_holiday.
 */
@Component
@RequiredArgsConstructor
public class WorkingTimeCalculator {

    private static final int HORIZON_DAYS = 120;

    private final CareSettings config;
    private final ClinicHolidayRepository holidayRepository;

    /** Cộng {@code hours} giờ làm việc vào {@code from}. */
    public OffsetDateTime addWorkingHours(OffsetDateTime from, int hours) {
        return addWorkingMinutes(from, hours * 60L);
    }

    /** Nếu {@code t} nằm ngoài giờ làm việc thì dời tới thời điểm mở cửa kế tiếp; ngược lại giữ nguyên. */
    public OffsetDateTime rollIntoWorkingHours(OffsetDateTime t) {
        return addWorkingMinutes(t, 0);
    }

    public OffsetDateTime addWorkingMinutes(OffsetDateTime from, long minutes) {
        if (minutes < 0) throw new IllegalArgumentException("minutes phải >= 0");
        LocalTime open = config.getTime("clinic_open_time", LocalTime.of(8, 0));
        LocalTime close = config.getTime("clinic_close_time", LocalTime.of(18, 0));
        if (!close.isAfter(open)) {
            open = LocalTime.of(8, 0);
            close = LocalTime.of(18, 0);
        }
        Set<Integer> workDays = new HashSet<>(config.getIntList("clinic_working_days", List.of(1, 2, 3, 4, 5, 6)));
        ZonedDateTime cursor = from.atZoneSameInstant(CareConstants.ZONE);

        long remaining = minutes;
        for (int i = 0; i < HORIZON_DAYS; i++) {
            LocalDate day = cursor.toLocalDate();
            if (!workDays.contains(day.getDayOfWeek().getValue()) || holidayRepository.existsByHolidayDate(day)) {
                cursor = day.plusDays(1).atTime(open).atZone(CareConstants.ZONE);
                continue;
            }
            ZonedDateTime dayOpen = day.atTime(open).atZone(CareConstants.ZONE);
            ZonedDateTime dayClose = day.atTime(close).atZone(CareConstants.ZONE);
            if (cursor.isBefore(dayOpen)) cursor = dayOpen;
            if (!cursor.isBefore(dayClose)) {
                cursor = day.plusDays(1).atTime(open).atZone(CareConstants.ZONE);
                continue;
            }
            long available = Duration.between(cursor, dayClose).toMinutes();
            if (remaining <= available) {
                return cursor.plusMinutes(remaining).toOffsetDateTime();
            }
            remaining -= available;
            cursor = day.plusDays(1).atTime(open).atZone(CareConstants.ZONE);
        }
        throw new IllegalStateException("Không tìm được mốc giờ làm việc trong " + HORIZON_DAYS
                + " ngày — kiểm tra clinic_working_days / clinic_holiday");
    }
}
