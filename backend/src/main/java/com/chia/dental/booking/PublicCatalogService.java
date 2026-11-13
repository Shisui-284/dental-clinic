package com.chia.dental.booking;

import com.chia.dental.booking.PublicDtos.*;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.*;
import com.chia.dental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicCatalogService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ServiceRepository serviceRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final ClinicHolidayRepository clinicHolidayRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final DentalChairRepository dentalChairRepository;
    private final AppointmentRepository appointmentRepository;

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

    public AvailableSlotsResponse getAvailableSlots(LocalDate date, List<Long> serviceIds, Long doctorId) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new BusinessException("INVALID_SERVICES", "Vui lòng chọn ít nhất một dịch vụ.", HttpStatus.BAD_REQUEST);
        }

        List<ServiceEntity> selectedServices = serviceRepository.findByServiceIdInAndIsActiveTrue(serviceIds);
        if (selectedServices.isEmpty()) {
            throw new BusinessException("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ hợp lệ.", HttpStatus.NOT_FOUND);
        }

        int totalDuration = selectedServices.stream().mapToInt(ServiceEntity::getDuration).sum();

        if (clinicHolidayRepository.existsByHolidayDate(date)) {
            return AvailableSlotsResponse.builder()
                    .date(date)
                    .totalDurationMinutes(totalDuration)
                    .holiday(true)
                    .holidayName("Ngày nghỉ lễ phòng khám")
                    .slots(List.of())
                    .build();
        }

        List<DentalChair> activeChairs = dentalChairRepository.findByStatus("ACTIVE");
        if (activeChairs.isEmpty()) {
            return AvailableSlotsResponse.builder()
                    .date(date)
                    .totalDurationMinutes(totalDuration)
                    .holiday(false)
                    .slots(List.of())
                    .build();
        }

        List<DoctorSchedule> schedules = doctorId != null
                ? doctorScheduleRepository.findByStaffStaffIdAndShiftDateAndStatus(doctorId, date, "ACTIVE")
                : doctorScheduleRepository.findByShiftDateAndStatus(date, "ACTIVE");

        OffsetDateTime startOfDay = date.atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        OffsetDateTime endOfDay = date.plusDays(1).atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        List<Appointment> dayAppointments = appointmentRepository.findActiveAppointmentsInRange(startOfDay, endOfDay);

        List<SlotDto> availableSlots = new ArrayList<>();

        for (DoctorSchedule schedule : schedules) {
            StaffAccount doctor = schedule.getStaff();
            if (!Boolean.TRUE.equals(doctor.getStatus())) {
                continue;
            }

            List<DoctorLeave> approvedLeaves = doctorLeaveRepository
                    .findByStaffStaffIdAndLeaveDateAndStatus(doctor.getStaffId(), date, "APPROVED");

            LocalTime cursor = schedule.getStartTime();
            while (!cursor.plusMinutes(totalDuration).isAfter(schedule.getEndTime())) {
                LocalTime slotEnd = cursor.plusMinutes(totalDuration);
                OffsetDateTime slotStartDt = ZonedDateTime.of(date, cursor, CLINIC_ZONE).toOffsetDateTime();
                OffsetDateTime slotEndDt = ZonedDateTime.of(date, slotEnd, CLINIC_ZONE).toOffsetDateTime();

                boolean onLeave = isDoctorOnLeave(approvedLeaves, cursor, slotEnd);
                boolean doctorBusy = isDoctorBusy(dayAppointments, doctor.getStaffId(), slotStartDt, slotEndDt);

                if (!onLeave && !doctorBusy) {
                    Long freeChairId = findFreeChairId(activeChairs, dayAppointments, slotStartDt, slotEndDt);
                    if (freeChairId != null) {
                        availableSlots.add(SlotDto.builder()
                                .startTime(cursor.format(TIME_FMT))
                                .endTime(slotEnd.format(TIME_FMT))
                                .doctorId(doctor.getStaffId())
                                .doctorName(doctor.getFullName())
                                .availableChairId(freeChairId)
                                .build());
                    }
                }
                cursor = cursor.plusMinutes(30);
            }
        }

        return AvailableSlotsResponse.builder()
                .date(date)
                .totalDurationMinutes(totalDuration)
                .holiday(false)
                .slots(availableSlots)
                .build();
    }

    private boolean isDoctorOnLeave(List<DoctorLeave> leaves, LocalTime start, LocalTime end) {
        return leaves.stream().anyMatch(l -> start.isBefore(l.getEndTime()) && end.isAfter(l.getStartTime()));
    }

    private boolean isDoctorBusy(List<Appointment> appts, Long doctorId, OffsetDateTime start, OffsetDateTime end) {
        return appts.stream()
                .filter(a -> a.getStaff().getStaffId().equals(doctorId))
                .anyMatch(a -> start.isBefore(a.getEndTime()) && end.isAfter(a.getDateTime()));
    }

    private Long findFreeChairId(List<DentalChair> chairs, List<Appointment> appts, OffsetDateTime start, OffsetDateTime end) {
        for (DentalChair chair : chairs) {
            boolean busy = appts.stream()
                    .filter(a -> a.getChair().getChairId().equals(chair.getChairId()))
                    .anyMatch(a -> start.isBefore(a.getEndTime()) && end.isAfter(a.getDateTime()));
            if (!busy) {
                return chair.getChairId();
            }
        }
        return null;
    }
}

