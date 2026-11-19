package com.chia.dental.care;

import com.chia.dental.care.support.CareSettings;
import com.chia.dental.care.support.WorkingTimeCalculator;
import com.chia.dental.repository.ClinicHolidayRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Giờ làm việc 08:00–18:00, T2–T7 (CN nghỉ). Múi giờ phòng khám UTC+7. */
class WorkingTimeCalculatorTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);

    private WorkingTimeCalculator calculator(Set<LocalDate> holidays) {
        CareSettings settings = new CareSettings(null, null, null) {
            @Override public LocalTime getTime(String key, LocalTime def) { return def; }
            @Override public List<Integer> getIntList(String key, List<Integer> def) { return def; }
        };
        ClinicHolidayRepository repo = Mockito.mock(ClinicHolidayRepository.class);
        Mockito.when(repo.existsByHolidayDate(Mockito.any())).thenAnswer(inv -> holidays.contains((LocalDate) inv.getArgument(0)));
        return new WorkingTimeCalculator(settings, repo);
    }

    private static OffsetDateTime at(int y, int m, int d, int h, int min) {
        return OffsetDateTime.of(y, m, d, h, min, 0, 0, VN);
    }

    @Test
    void fortyEightWorkingHoursSkipsNightsAndSunday() {
        // Thứ 6 20/11/2026 16:00: T6 2h + T7 10h + T2 10h + T3 10h + T4 10h = 42h, còn 6h -> Thứ 5 26/11 14:00
        assertEquals(at(2026, 11, 26, 14, 0), calculator(Set.of()).addWorkingHours(at(2026, 11, 20, 16, 0), 48));
    }

    @Test
    void startOnSundayRollsToMondayOpening() {
        assertEquals(at(2026, 11, 23, 8, 0), calculator(Set.of()).rollIntoWorkingHours(at(2026, 11, 22, 3, 0)));
    }

    @Test
    void startAfterClosingRollsToNextDayOpening() {
        assertEquals(at(2026, 11, 20, 8, 0), calculator(Set.of()).rollIntoWorkingHours(at(2026, 11, 19, 19, 30)));
    }

    @Test
    void insideWorkingHoursIsUnchanged() {
        assertEquals(at(2026, 11, 19, 10, 15), calculator(Set.of()).rollIntoWorkingHours(at(2026, 11, 19, 10, 15)));
    }

    @Test
    void holidaysAreExcluded() {
        // CN 22/11 09:00 + Thứ 2 23/11 là ngày nghỉ lễ -> mốc làm việc kế tiếp là Thứ 3 24/11 08:00
        OffsetDateTime r = calculator(Set.of(LocalDate.of(2026, 11, 23))).rollIntoWorkingHours(at(2026, 11, 22, 9, 0));
        assertEquals(at(2026, 11, 24, 8, 0), r);
    }

    @Test
    void withinSameDay() {
        assertEquals(at(2026, 11, 19, 13, 0), calculator(Set.of()).addWorkingHours(at(2026, 11, 19, 9, 0), 4));
    }
}
