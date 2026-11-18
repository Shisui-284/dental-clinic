package com.chia.dental.booking;

import com.chia.dental.booking.PublicDtos.*;
import com.chia.dental.common.config.ClinicConfigService;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.*;
import com.chia.dental.repository.*;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PublicCatalogService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_DAYS_PER_QUERY = 14;
    private static final int SLOT_STEP_MINUTES = 30;

    private final ServiceRepository serviceRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final ClinicHolidayRepository clinicHolidayRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final DentalChairRepository dentalChairRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final ClinicConfigService clinicConfigService;

    public List<ServiceDto> getActiveServices() {
        return serviceRepository.findByIsActiveTrueOrderByServiceIdAsc().stream()
                .map(s -> ServiceDto.builder()
                        .serviceId(s.getServiceId())
                        .name(s.getName())
                        .duration(s.getDuration())
                        .price(s.getPrice())
                        .followupCycleDays(s.getFollowupCycleDays())
                        .build())
                .toList();
    }

    public List<DoctorDto> getActiveDoctors() {
        return staffAccountRepository.findByRoleRoleNameAndStatusTrue("DOCTOR").stream()
                .map(d -> DoctorDto.builder()
                        .staffId(d.getStaffId())
                        .username(d.getUsername())
                        .fullName(d.getFullName())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public AvailableSlotsResponse getAvailableSlots(
            LocalDate fromDate,
            LocalDate toDate,
            List<Long> serviceIds,
            Long staffId,
            String phone,
            String patientFullName,
            Short birthYear,
            Long patientId,
            Boolean forceNewPatient,
            UserPrincipal principal) {

        if (fromDate == null) {
            throw new BusinessException("INVALID_DATE", "Vui lòng truyền tham số fromDate hoặc date.", HttpStatus.BAD_REQUEST);
        }
        LocalDate effectiveToDate = toDate != null ? toDate : fromDate;
        if (effectiveToDate.isBefore(fromDate)) {
            throw new BusinessException("INVALID_DATE_RANGE", "toDate phải lớn hơn hoặc bằng fromDate.", HttpStatus.BAD_REQUEST);
        }

        boolean isStaff = principal != null && "STAFF".equals(principal.getType());
        long daySpan = ChronoUnit.DAYS.between(fromDate, effectiveToDate) + 1;
        if (!isStaff && daySpan > MAX_DAYS_PER_QUERY) {
            throw new BusinessException("INVALID_DATE_RANGE",
                    "Mỗi lần tra cứu chỉ được tối đa " + MAX_DAYS_PER_QUERY + " ngày.",
                    HttpStatus.BAD_REQUEST);
        }

        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new BusinessException("INVALID_SERVICES", "Vui lòng chọn ít nhất một dịch vụ.", HttpStatus.BAD_REQUEST);
        }

        List<ServiceEntity> selectedServices = serviceRepository.findByServiceIdInAndIsActiveTrue(serviceIds);
        if (selectedServices.size() != new HashSet<>(serviceIds).size()) {
            throw new BusinessException("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ hợp lệ.", HttpStatus.NOT_FOUND);
        }

        int serviceDuration = selectedServices.stream().mapToInt(ServiceEntity::getDuration).sum();
        int chairBuffer = clinicConfigService.getChairBufferMinutes();
        boolean isNewPatient = determineIsNewPatient(patientId, phone, patientFullName, birthYear, forceNewPatient);
        int newPatientBuffer = isNewPatient ? clinicConfigService.getNewPatientBufferMinutes() : 0;
        int totalDuration = serviceDuration + chairBuffer + newPatientBuffer;

        LocalTime clinicOpen = clinicConfigService.getClinicOpenTime();
        LocalTime clinicClose = clinicConfigService.getClinicCloseTime();
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        OffsetDateTime minAllowedStart = now.plusHours(clinicConfigService.getBookingMinLeadHours());
        OffsetDateTime maxAllowedStart = now.plusDays(clinicConfigService.getBookingMaxLeadDays());

        List<DentalChair> activeChairs = dentalChairRepository.findByStatus("ACTIVE");
        Map<LocalDate, String> holidayMap = new HashMap<>();
        for (ClinicHoliday h : clinicHolidayRepository.findByHolidayDateBetween(fromDate, effectiveToDate)) {
            holidayMap.put(h.getHolidayDate(), h.getName());
        }

        List<DaySlotsDto> days = new ArrayList<>();
        for (LocalDate day = fromDate; !day.isAfter(effectiveToDate); day = day.plusDays(1)) {
            if (holidayMap.containsKey(day)) {
                days.add(DaySlotsDto.builder()
                        .date(day)
                        .isHoliday(true)
                        .holidayName(holidayMap.get(day))
                        .slots(List.of())
                        .build());
                continue;
            }

            if (activeChairs.isEmpty()) {
                days.add(DaySlotsDto.builder()
                        .date(day)
                        .isHoliday(false)
                        .slots(List.of())
                        .build());
                continue;
            }

            List<SlotDto> daySlots = computeSlotsForDay(
                    day, totalDuration, staffId, clinicOpen, clinicClose,
                    activeChairs, isStaff, minAllowedStart, maxAllowedStart);

            days.add(DaySlotsDto.builder()
                    .date(day)
                    .isHoliday(false)
                    .slots(daySlots)
                    .build());
        }

        DaySlotsDto firstDay = days.get(0);
        List<SlotDto> legacyAvailableSlots = firstDay.getSlots().stream()
                .filter(SlotDto::isAvailable)
                .toList();

        return AvailableSlotsResponse.builder()
                .serviceDurationMinutes(serviceDuration)
                .chairBufferMinutes(chairBuffer)
                .newPatientBufferMinutes(newPatientBuffer)
                .totalCalculatedDuration(totalDuration)
                .isNewPatient(isNewPatient)
                .days(days)
                .date(firstDay.getDate())
                .totalDurationMinutes(totalDuration)
                .holiday(firstDay.isHoliday())
                .holidayName(firstDay.getHolidayName())
                .slots(legacyAvailableSlots)
                .build();
    }

    private List<SlotDto> computeSlotsForDay(
            LocalDate day,
            int totalDuration,
            Long staffId,
            LocalTime clinicOpen,
            LocalTime clinicClose,
            List<DentalChair> activeChairs,
            boolean isStaff,
            OffsetDateTime minAllowedStart,
            OffsetDateTime maxAllowedStart) {

        List<DoctorSchedule> schedules = (staffId != null
                ? doctorScheduleRepository.findByStaffStaffIdAndShiftDateAndStatus(staffId, day, "ACTIVE")
                : doctorScheduleRepository.findByShiftDateAndStatus(day, "ACTIVE"))
                .stream()
                .filter(sc -> Boolean.TRUE.equals(sc.getStaff().getStatus())
                        && "DOCTOR".equals(sc.getStaff().getRole().getRoleName()))
                .toList();

        if (schedules.isEmpty()) {
            return List.of();
        }

        OffsetDateTime startOfDay = day.atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        OffsetDateTime endOfDay = day.plusDays(1).atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        List<Appointment> dayAppointments = appointmentRepository.findActiveAppointmentsInRange(startOfDay, endOfDay);

        Map<Long, List<DoctorLeave>> leavesByDoctor = new HashMap<>();
        for (DoctorSchedule sc : schedules) {
            Long docId = sc.getStaff().getStaffId();
            leavesByDoctor.computeIfAbsent(docId, id ->
                    doctorLeaveRepository.findByStaffStaffIdAndLeaveDateAndStatus(id, day, "APPROVED"));
        }

        // Tạo các mốc giờ từ giờ mở cửa đến giờ đóng cửa (bước 30 phút)
        List<SlotDto> result = new ArrayList<>();
        LocalTime cursor = clinicOpen;
        while (!cursor.plusMinutes(totalDuration).isAfter(clinicClose)) {
            final LocalTime slotStart = cursor;
            final LocalTime slotEnd = slotStart.plusMinutes(totalDuration);
            final OffsetDateTime slotStartDt = ZonedDateTime.of(day, slotStart, CLINIC_ZONE).toOffsetDateTime();
            final OffsetDateTime slotEndDt = ZonedDateTime.of(day, slotEnd, CLINIC_ZONE).toOffsetDateTime();

            boolean withinBookingWindow = isStaff
                    || (!slotStartDt.isBefore(minAllowedStart) && !slotStartDt.isAfter(maxAllowedStart));

            Long freeChairId = findFreeChairId(activeChairs, dayAppointments, slotStartDt, slotEndDt);

            if (staffId != null) {
                DoctorSchedule matchedSchedule = findCoveringSchedule(schedules, slotStart, slotEnd);
                if (matchedSchedule != null) {
                    StaffAccount doc = matchedSchedule.getStaff();
                    boolean free = withinBookingWindow
                            && freeChairId != null
                            && !isDoctorOnLeave(leavesByDoctor.getOrDefault(doc.getStaffId(), List.of()), slotStart, slotEnd)
                            && !isDoctorBusy(dayAppointments, doc.getStaffId(), slotStartDt, slotEndDt);

                    result.add(SlotDto.builder()
                            .startTime(slotStart.format(TIME_FMT))
                            .endTime(slotEnd.format(TIME_FMT))
                            .period(resolvePeriod(slotStart))
                            .available(free)
                            .doctorId(doc.getStaffId())
                            .doctorName(doc.getFullName())
                            .availableChairId(isStaff && free ? freeChairId : null)
                            .build());
                }
            } else {
                // Khách chưa chọn bác sĩ: chỉ cần có ít nhất 1 bác sĩ có ca & rảnh + 1 ghế trống
                List<DoctorSchedule> coveringSchedules = schedules.stream()
                        .filter(sc -> !slotStart.isBefore(sc.getStartTime()) && !slotEnd.isAfter(sc.getEndTime()))
                        .toList();

                if (!coveringSchedules.isEmpty()) {
                    StaffAccount pickedDoctor = null;
                    if (withinBookingWindow && freeChairId != null) {
                        pickedDoctor = coveringSchedules.stream()
                                .map(DoctorSchedule::getStaff)
                                .filter(doc -> !isDoctorOnLeave(leavesByDoctor.getOrDefault(doc.getStaffId(), List.of()), slotStart, slotEnd))
                                .filter(doc -> !isDoctorBusy(dayAppointments, doc.getStaffId(), slotStartDt, slotEndDt))
                                .min(Comparator.comparingLong((StaffAccount d) -> countDoctorAppointments(dayAppointments, d.getStaffId()))
                                        .thenComparing(StaffAccount::getStaffId))
                                .orElse(null);
                    }

                    boolean free = pickedDoctor != null;
                    result.add(SlotDto.builder()
                            .startTime(slotStart.format(TIME_FMT))
                            .endTime(slotEnd.format(TIME_FMT))
                            .period(resolvePeriod(slotStart))
                            .available(free)
                            .doctorId(free ? pickedDoctor.getStaffId() : null)
                            .doctorName(free ? pickedDoctor.getFullName() : null)
                            .availableChairId(isStaff && free ? freeChairId : null)
                            .build());
                }
            }

            cursor = cursor.plusMinutes(SLOT_STEP_MINUTES);
        }

        return result;
    }

    private boolean determineIsNewPatient(
            Long patientId,
            String phone,
            String patientFullName,
            Short birthYear,
            Boolean forceNewPatient) {
        if (Boolean.TRUE.equals(forceNewPatient)) {
            return true;
        }
        if (patientId != null) {
            return !patientProfileRepository.existsById(patientId);
        }
        if (phone != null && !phone.isBlank()) {
            if (patientFullName != null && !patientFullName.isBlank() && birthYear != null) {
                return !patientProfileRepository.existsByCustomerPhoneAndFullNameIgnoreCaseAndBirthYear(
                        phone.trim(), patientFullName.trim(), birthYear);
            }
            return !patientProfileRepository.existsByCustomerPhone(phone.trim());
        }
        return false;
    }

    private DoctorSchedule findCoveringSchedule(List<DoctorSchedule> schedules, LocalTime start, LocalTime end) {
        return schedules.stream()
                .filter(sc -> !start.isBefore(sc.getStartTime()) && !end.isAfter(sc.getEndTime()))
                .findFirst()
                .orElse(null);
    }

    private String resolvePeriod(LocalTime time) {
        if (time.isBefore(LocalTime.NOON)) {
            return "MORNING";
        }
        if (time.isBefore(LocalTime.of(17, 0))) {
            return "AFTERNOON";
        }
        return "EVENING";
    }

    private long countDoctorAppointments(List<Appointment> appts, Long doctorId) {
        return appts.stream().filter(a -> a.getStaff().getStaffId().equals(doctorId)).count();
    }

    private boolean isDoctorOnLeave(List<DoctorLeave> leaves, LocalTime start, LocalTime end) {
        return leaves.stream().anyMatch(l -> start.isBefore(l.getEndTime()) && end.isAfter(l.getStartTime()));
    }

    private boolean isDoctorBusy(List<Appointment> appts, Long doctorId, OffsetDateTime start, OffsetDateTime end) {
        return appts.stream()
                .filter(a -> a.getStaff().getStaffId().equals(doctorId))
                .anyMatch(a -> start.isBefore(getEffectiveEndTime(a)) && end.isAfter(a.getDateTime()));
    }

    private Long findFreeChairId(List<DentalChair> chairs, List<Appointment> appts, OffsetDateTime start, OffsetDateTime end) {
        for (DentalChair chair : chairs) {
            boolean busy = appts.stream()
                    .filter(a -> a.getChair().getChairId().equals(chair.getChairId()))
                    .anyMatch(a -> start.isBefore(getEffectiveEndTime(a)) && end.isAfter(a.getDateTime()));
            if (!busy) {
                return chair.getChairId();
            }
        }
        return null;
    }

    private OffsetDateTime getEffectiveEndTime(Appointment a) {
        return a.getEndTime() != null ? a.getEndTime() : a.getDateTime().plusMinutes(a.getDuration());
    }
}
