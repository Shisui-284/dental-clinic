package com.chia.dental.booking;

import com.chia.dental.booking.AppointmentDtos.*;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.*;
import com.chia.dental.repository.*;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentBookingService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AppointmentRepository appointmentRepository;
    private final AppointmentServiceRepository appointmentServiceRepository;
    private final CustomerRepository customerRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final DentalChairRepository dentalChairRepository;
    private final ServiceRepository serviceRepository;
    private final ClinicHolidayRepository clinicHolidayRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;

    @Transactional
    public AppointmentResponse createCustomerAppointment(UserPrincipal principal, CreateAppointmentRequest req) {
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        Customer customer = customerRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Không tìm thấy tài khoản khách hàng.", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(customer.getIsBlacklisted())) {
            throw new BusinessException("CUSTOMER_BLACKLISTED", "Số điện thoại đang bị hạn chế tự đặt lịch trực tuyến.", HttpStatus.FORBIDDEN);
        }

        if (req.getDateTime().isBefore(now)) {
            throw new BusinessException("INVALID_DATETIME", "Thời gian đặt lịch phải ở tương lai.", HttpStatus.BAD_REQUEST);
        }

        LocalDate apptDate = req.getDateTime().atZoneSameInstant(CLINIC_ZONE).toLocalDate();
        if (clinicHolidayRepository.existsByHolidayDate(apptDate)) {
            throw new BusinessException("CLINIC_HOLIDAY", "Ngày này phòng khám nghỉ lễ, không nhận đặt lịch.", HttpStatus.BAD_REQUEST);
        }

        PatientProfile patient = resolvePatientProfile(customer, req);

        StaffAccount doctor = staffAccountRepository.findById(req.getDoctorId())
                .filter(s -> Boolean.TRUE.equals(s.getStatus()) && "DOCTOR".equals(s.getRole().getRoleName()))
                .orElseThrow(() -> new BusinessException("DOCTOR_NOT_FOUND", "Bác sĩ không tồn tại hoặc đang ngừng hoạt động.", HttpStatus.NOT_FOUND));

        List<ServiceEntity> services = serviceRepository.findByServiceIdInAndIsActiveTrue(req.getServiceIds());
        if (services.size() != req.getServiceIds().size()) {
            throw new BusinessException("SERVICE_NOT_FOUND", "Có dịch vụ không tồn tại hoặc đã ngừng cung cấp.", HttpStatus.BAD_REQUEST);
        }

        short totalDuration = (short) services.stream().mapToInt(ServiceEntity::getDuration).sum();
        OffsetDateTime slotEnd = req.getDateTime().plusMinutes(totalDuration);

        checkDoctorLeave(doctor.getStaffId(), req.getDateTime(), slotEnd);

        DentalChair chair = resolveAvailableChair(req.getChairId(), req.getDateTime(), slotEnd);

        OffsetDateTime holdUntil = now.plusMinutes(15);
        if (holdUntil.isAfter(req.getDateTime())) {
            holdUntil = req.getDateTime();
        }

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .staff(doctor)
                .chair(chair)
                .dateTime(req.getDateTime())
                .duration(totalDuration)
                .status("PENDING")
                .source("ONLINE")
                .bookedByPhone(customer.getPhone())
                .holdUntil(holdUntil)
                .isPriority(false)
                .note(req.getNote())
                .createdAt(now)
                .build();

        appointment = appointmentRepository.saveAndFlush(appointment);

        List<AppointmentServiceEntity> savedServices = new ArrayList<>();
        for (ServiceEntity svc : services) {
            AppointmentServiceEntity item = AppointmentServiceEntity.builder()
                    .appointment(appointment)
                    .service(svc)
                    .isTemporary(false)
                    .durationSnapshot(svc.getDuration())
                    .priceSnapshot(svc.getPrice())
                    .isPerformed(false)
                    .build();
            savedServices.add(appointmentServiceRepository.save(item));
        }

        return toResponse(appointment, savedServices);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getCustomerAppointments(UserPrincipal principal) {
        return appointmentRepository.findByCustomerId(principal.getId()).stream()
                .map(a -> toResponse(a, appointmentServiceRepository.findByAppointmentId(a.getAppointmentId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getStaffAppointments(String status) {
        return appointmentRepository.findAllWithDetails(status).stream()
                .map(a -> toResponse(a, appointmentServiceRepository.findByAppointmentId(a.getAppointmentId())))
                .toList();
    }

    @Transactional
    public AppointmentResponse confirmAppointment(Long appointmentId, UserPrincipal principal, ConfirmAppointmentRequest req) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        if (!"PENDING".equals(appt.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Chỉ có thể duyệt lịch đang ở trạng thái PENDING (hiện tại: " + appt.getStatus() + ").",
                    HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        if (appt.getHoldUntil() != null && appt.getHoldUntil().isBefore(now)) {
            throw new BusinessException("APPT_HOLD_EXPIRED",
                    "Lịch chờ xác nhận đã quá hạn giữ chỗ (hold_until).",
                    HttpStatus.CONFLICT);
        }

        if (req != null && req.getDoctorId() != null) {
            StaffAccount newDoctor = staffAccountRepository.findById(req.getDoctorId())
                    .filter(s -> Boolean.TRUE.equals(s.getStatus()) && "DOCTOR".equals(s.getRole().getRoleName()))
                    .orElseThrow(() -> new BusinessException("DOCTOR_NOT_FOUND", "Bác sĩ không hợp lệ.", HttpStatus.NOT_FOUND));
            appt.setStaff(newDoctor);
        }

        if (req != null && req.getChairId() != null) {
            DentalChair newChair = dentalChairRepository.findById(req.getChairId())
                    .filter(c -> "ACTIVE".equals(c.getStatus()))
                    .orElseThrow(() -> new BusinessException("CHAIR_NOT_FOUND", "Ghế nha không hợp lệ hoặc đang bảo trì.", HttpStatus.NOT_FOUND));
            appt.setChair(newChair);
        }

        OffsetDateTime end = appt.getDateTime().plusMinutes(appt.getDuration());
        checkDoctorLeave(appt.getStaff().getStaffId(), appt.getDateTime(), end);

        appt.setStatus("CONFIRMED");
        appt = appointmentRepository.saveAndFlush(appt);

        List<AppointmentServiceEntity> services = appointmentServiceRepository.findByAppointmentId(appt.getAppointmentId());
        return toResponse(appt, services);
    }

    @Transactional
    public AppointmentResponse staffCancelAppointment(Long appointmentId, UserPrincipal principal, CancelAppointmentRequest req) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        if ("COMPLETED".equals(appt.getStatus()) || "CANCELLED".equals(appt.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Không thể hủy lịch ở trạng thái " + appt.getStatus(), HttpStatus.BAD_REQUEST);
        }

        StaffAccount staff = staffAccountRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "Không tìm thấy thông tin nhân sự.", HttpStatus.NOT_FOUND));

        appt.setStatus("CANCELLED");
        appt.setCancelledByType("STAFF");
        appt.setCancelledBy(staff);
        appt.setCancelReason(req.getCancelReason());
        appt = appointmentRepository.saveAndFlush(appt);

        return toResponse(appt, appointmentServiceRepository.findByAppointmentId(appt.getAppointmentId()));
    }

    private PatientProfile resolvePatientProfile(Customer customer, CreateAppointmentRequest req) {
        if (req.getPatientId() != null) {
            return patientProfileRepository.findByPatientIdAndCustomerCustomerId(req.getPatientId(), customer.getCustomerId())
                    .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Không tìm thấy hồ sơ bệnh nhân thuộc tài khoản của bạn.", HttpStatus.NOT_FOUND));
        }

        if (req.getPatientFullName() == null || req.getPatientFullName().isBlank() || req.getBirthYear() == null) {
            throw new BusinessException("PATIENT_INFO_REQUIRED",
                    "Vui lòng chọn patientId hoặc cung cấp patientFullName và birthYear để tạo hồ sơ mới.",
                    HttpStatus.BAD_REQUEST);
        }

        return patientProfileRepository.save(PatientProfile.builder()
                .customer(customer)
                .fullName(req.getPatientFullName().trim())
                .birthYear(req.getBirthYear())
                .gender(req.getGender())
                .build());
    }

    private void checkDoctorLeave(Long doctorId, OffsetDateTime start, OffsetDateTime end) {
        LocalDate date = start.atZoneSameInstant(CLINIC_ZONE).toLocalDate();
        LocalTime startTime = start.atZoneSameInstant(CLINIC_ZONE).toLocalTime();
        LocalTime endTime = end.atZoneSameInstant(CLINIC_ZONE).toLocalTime();

        boolean onLeave = doctorLeaveRepository
                .findByStaffStaffIdAndLeaveDateAndStatus(doctorId, date, "APPROVED")
                .stream()
                .anyMatch(l -> startTime.isBefore(l.getEndTime()) && endTime.isAfter(l.getStartTime()));

        if (onLeave) {
            throw new BusinessException("APPT_DOCTOR_ON_LEAVE",
                    "Bác sĩ phụ trách có lịch nghỉ đột xuất trong khung giờ này.",
                    HttpStatus.CONFLICT);
        }
    }

    private DentalChair resolveAvailableChair(Long chairId, OffsetDateTime start, OffsetDateTime end) {
        if (chairId != null) {
            return dentalChairRepository.findById(chairId)
                    .filter(c -> "ACTIVE".equals(c.getStatus()))
                    .orElseThrow(() -> new BusinessException("CHAIR_NOT_FOUND", "Ghế nha không tồn tại hoặc đang bảo trì.", HttpStatus.NOT_FOUND));
        }

        List<DentalChair> activeChairs = dentalChairRepository.findByStatus("ACTIVE");
        if (activeChairs.isEmpty()) {
            throw new BusinessException("NO_ACTIVE_CHAIR", "Không có ghế nha khả dụng.", HttpStatus.CONFLICT);
        }

        LocalDate date = start.atZoneSameInstant(CLINIC_ZONE).toLocalDate();
        OffsetDateTime dayStart = date.atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        List<Appointment> dayAppts = appointmentRepository.findActiveAppointmentsInRange(dayStart, dayEnd);

        for (DentalChair chair : activeChairs) {
            boolean busy = dayAppts.stream()
                    .filter(a -> a.getChair().getChairId().equals(chair.getChairId()))
                    .anyMatch(a -> start.isBefore(a.getEndTime()) && end.isAfter(a.getDateTime()));
            if (!busy) {
                return chair;
            }
        }
        throw new BusinessException("APPT_SLOT_CONFLICT", "Tất cả ghế nha đã kín trong khung giờ này.", HttpStatus.CONFLICT);
    }

    private AppointmentResponse toResponse(Appointment a, List<AppointmentServiceEntity> services) {
        OffsetDateTime computedEnd = a.getEndTime() != null ? a.getEndTime() : a.getDateTime().plusMinutes(a.getDuration());
        return AppointmentResponse.builder()
                .appointmentId(a.getAppointmentId())
                .patientId(a.getPatient().getPatientId())
                .patientName(a.getPatient().getFullName())
                .bookedByPhone(a.getBookedByPhone())
                .doctorId(a.getStaff().getStaffId())
                .doctorName(a.getStaff().getFullName())
                .chairId(a.getChair().getChairId())
                .chairName(a.getChair().getChairName())
                .dateTime(a.getDateTime())
                .duration(a.getDuration())
                .endTime(computedEnd)
                .status(a.getStatus())
                .source(a.getSource())
                .holdUntil(a.getHoldUntil())
                .note(a.getNote())
                .createdAt(a.getCreatedAt())
                .services(services.stream()
                        .map(s -> ServiceItemDto.builder()
                                .serviceId(s.getService() != null ? s.getService().getServiceId() : null)
                                .serviceName(s.getService() != null ? s.getService().getName() : "Dịch vụ tạm")
                                .durationSnapshot(s.getDurationSnapshot())
                                .priceSnapshot(s.getPriceSnapshot())
                                .build())
                        .toList())
                .build();
    }
}

