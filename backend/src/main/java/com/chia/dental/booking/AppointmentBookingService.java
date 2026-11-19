package com.chia.dental.booking;

import com.chia.dental.booking.AppointmentDtos.*;
import com.chia.dental.common.audit.AuditLogService;
import com.chia.dental.common.config.ClinicConfigService;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.*;
import com.chia.dental.repository.*;
import com.chia.dental.schedule.LeaveConflictService;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppointmentBookingService {

    public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String AUTO_ASSIGNED_TAG = "[AUTO_DOCTOR]";

    private final AppointmentRepository appointmentRepository;
    private final AppointmentServiceRepository appointmentServiceRepository;
    private final CustomerRepository customerRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final ConsentRepository consentRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final DentalChairRepository dentalChairRepository;
    private final ServiceRepository serviceRepository;
    private final ClinicHolidayRepository clinicHolidayRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final NoShowEventRepository noShowEventRepository;
    private final LeaveConflictService leaveConflictService;
    private final ClinicConfigService clinicConfigService;
    private final AuditLogService auditLogService;
    private final IcsCalendarService icsCalendarService;

    // =========================================================================
    // 1. CỔNG KHÁCH HÀNG (MÀN K5, K6, K7)
    // =========================================================================

    @Transactional
    public AppointmentResponse createCustomerAppointment(UserPrincipal principal, CreateAppointmentRequest req) {
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        Customer customer = customerRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Không tìm thấy tài khoản khách hàng.", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(customer.getIsBlacklisted())) {
            throw new BusinessException("CUSTOMER_BLACKLISTED",
                    "Số điện thoại đang bị hạn chế tự đặt lịch trực tuyến. Vui lòng liên hệ hotline phòng khám.",
                    HttpStatus.FORBIDDEN);
        }

        validateCustomerBookingWindow(req.getDateTime(), now);

        LocalDate apptDate = req.getDateTime().atZoneSameInstant(CLINIC_ZONE).toLocalDate();
        if (clinicHolidayRepository.existsByHolidayDate(apptDate)) {
            throw new BusinessException("CLINIC_HOLIDAY", "Ngày này phòng khám nghỉ lễ, không nhận đặt lịch.", HttpStatus.BAD_REQUEST);
        }

        ResolvedPatient resolvedPatient = resolveCustomerPatientProfile(customer, req, now);
        PatientProfile patient = resolvedPatient.patient();
        boolean isNewPatient = resolvedPatient.isNewPatient();

        List<ServiceEntity> services = serviceRepository.findByServiceIdInAndIsActiveTrue(req.getServiceIds());
        if (services.size() != new HashSet<>(req.getServiceIds()).size()) {
            throw new BusinessException("SERVICE_NOT_FOUND", "Có dịch vụ không tồn tại hoặc đã ngừng cung cấp.", HttpStatus.BAD_REQUEST);
        }

        int serviceDuration = services.stream().mapToInt(ServiceEntity::getDuration).sum();
        int chairBuffer = clinicConfigService.getChairBufferMinutes();
        int newPatientBuffer = isNewPatient ? clinicConfigService.getNewPatientBufferMinutes() : 0;
        short totalDuration = (short) (serviceDuration + chairBuffer + newPatientBuffer);

        OffsetDateTime slotStart = req.getDateTime();
        OffsetDateTime slotEnd = slotStart.plusMinutes(totalDuration);

        validateWithinClinicHours(slotStart, slotEnd);

        DoctorAssignment assignment = resolveDoctorForSlot(req.getStaffId(), slotStart, slotEnd, null, true);
        StaffAccount doctor = assignment.doctor();
        boolean autoAssigned = assignment.autoAssigned();

        DentalChair chair = resolveAvailableChair(req.getChairId(), slotStart, slotEnd, null);
        OffsetDateTime holdUntil = calculateHoldUntil(now, slotStart);

        String storedNote = buildStoredNote(req.getNote(), autoAssigned);

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .staff(doctor)
                .chair(chair)
                .dateTime(slotStart)
                .duration(totalDuration)
                .status("PENDING")
                .source("ONLINE")
                .bookedByPhone(customer.getPhone())
                .holdUntil(holdUntil)
                .isPriority(false)
                .note(storedNote)
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

        Map<String, Object> auditNew = new LinkedHashMap<>();
        auditNew.put("status", appointment.getStatus());
        auditNew.put("source", appointment.getSource());
        auditNew.put("dateTime", appointment.getDateTime().toString());
        auditNew.put("duration", appointment.getDuration());
        auditNew.put("staffId", doctor.getStaffId());
        auditNew.put("chairId", chair.getChairId());
        auditNew.put("doctorAutoAssigned", autoAssigned);
        auditNew.put("holdUntil", holdUntil.toString());
        auditLogService.logCustomer(customer.getCustomerId(), "APPOINTMENT_CREATE", "appointment",
                appointment.getAppointmentId(), null, auditNew);

        return toResponse(appointment, savedServices, false, false, null);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getCustomerAppointments(UserPrincipal principal, String tab) {
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        return appointmentRepository.findByCustomerId(principal.getId()).stream()
                .filter(a -> matchesCustomerTab(a, tab, now))
                .map(a -> toResponse(a, appointmentServiceRepository.findByAppointmentId(a.getAppointmentId()), false, false, null))
                .toList();
    }

    @Transactional
    public AppointmentResponse rescheduleCustomerAppointment(
            Long appointmentId,
            UserPrincipal principal,
            CustomerRescheduleRequest req) {

        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        verifyCustomerOwnership(appt, principal);

        if (!"PENDING".equals(appt.getStatus()) && !"CONFIRMED".equals(appt.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Chỉ có thể đổi lịch khi lịch hẹn ở trạng thái PENDING hoặc CONFIRMED (hiện tại: " + appt.getStatus() + ").",
                    HttpStatus.BAD_REQUEST);
        }

        validateCustomerChangeWindow(appt.getDateTime(), now, "đổi lịch");
        validateCustomerBookingWindow(req.getDateTime(), now);

        LocalDate newDate = req.getDateTime().atZoneSameInstant(CLINIC_ZONE).toLocalDate();
        if (clinicHolidayRepository.existsByHolidayDate(newDate)) {
            throw new BusinessException("CLINIC_HOLIDAY", "Ngày mới rơi vào ngày nghỉ lễ của phòng khám.", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime newStart = req.getDateTime();
        OffsetDateTime newEnd = newStart.plusMinutes(appt.getDuration());
        validateWithinClinicHours(newStart, newEnd);

        Map<String, Object> oldSnapshot = Map.of(
                "dateTime", appt.getDateTime().toString(),
                "staffId", appt.getStaff().getStaffId(),
                "chairId", appt.getChair().getChairId(),
                "status", appt.getStatus()
        );

        Long requestedDoctorId = req.getStaffId();
        DoctorAssignment assignment;
        if (requestedDoctorId != null) {
            assignment = resolveDoctorForSlot(requestedDoctorId, newStart, newEnd, appt.getAppointmentId(), true);
        } else {
            try {
                assignment = resolveDoctorForSlot(appt.getStaff().getStaffId(), newStart, newEnd, appt.getAppointmentId(), true);
            } catch (BusinessException ex) {
                assignment = resolveDoctorForSlot(null, newStart, newEnd, appt.getAppointmentId(), true);
            }
        }
        DentalChair newChair = resolveAvailableChair(null, newStart, newEnd, appt.getAppointmentId());

        appt.setDateTime(newStart);
        appt.setStaff(assignment.doctor());
        appt.setChair(newChair);

        if ("PENDING".equals(appt.getStatus())) {
            appt.setHoldUntil(calculateHoldUntil(now, newStart));
        }
        if (req.getNote() != null) {
            appt.setNote(buildStoredNote(req.getNote(), assignment.autoAssigned()));
        }

        appt = appointmentRepository.saveAndFlush(appt);

        Map<String, Object> newSnapshot = Map.of(
                "dateTime", appt.getDateTime().toString(),
                "staffId", appt.getStaff().getStaffId(),
                "chairId", appt.getChair().getChairId(),
                "status", appt.getStatus()
        );
        auditLogService.logCustomer(principal.getId(), "APPOINTMENT_RESCHEDULE", "appointment",
                appt.getAppointmentId(), oldSnapshot, newSnapshot);

        return toResponse(appt, appointmentServiceRepository.findByAppointmentId(appt.getAppointmentId()), false, false, null);
    }

    @Transactional
    public AppointmentResponse cancelCustomerAppointment(
            Long appointmentId,
            UserPrincipal principal,
            CancelAppointmentRequest req) {

        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        verifyCustomerOwnership(appt, principal);

        if (!"PENDING".equals(appt.getStatus()) && !"CONFIRMED".equals(appt.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Không thể hủy lịch ở trạng thái " + appt.getStatus() + ".",
                    HttpStatus.BAD_REQUEST);
        }

        validateCustomerChangeWindow(appt.getDateTime(), now, "hủy lịch");

        String oldStatus = appt.getStatus();
        appt.setStatus("CANCELLED");
        appt.setCancelledByType("CUSTOMER");
        appt.setCancelledBy(null);
        appt.setCancelReason(req.getCancelReason().trim());
        appt = appointmentRepository.saveAndFlush(appt);

        auditLogService.logCustomer(
                principal.getId(),
                "APPOINTMENT_CANCEL",
                "appointment",
                appt.getAppointmentId(),
                Map.of("status", oldStatus),
                Map.of("status", "CANCELLED", "cancelledByType", "CUSTOMER", "cancelReason", appt.getCancelReason())
        );

        return toResponse(appt, appointmentServiceRepository.findByAppointmentId(appt.getAppointmentId()), false, false, null);
    }

    @Transactional(readOnly = true)
    public IcsDownloadPayload exportCustomerAppointmentIcs(Long appointmentId, UserPrincipal principal) {
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        verifyCustomerOwnership(appt, principal);

        List<AppointmentServiceEntity> services = appointmentServiceRepository.findByAppointmentId(appointmentId);
        String code = formatAppointmentCode(appt);
        String content = icsCalendarService.buildAppointmentIcs(appt, services, code);
        return new IcsDownloadPayload("appointment-" + code + ".ics", content);
    }

    // =========================================================================
    // 2. CỔNG NHÂN SỰ / LỄ TÂN ĐIỀU PHỐI (MÀN L1, L2, L3)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getStaffAppointments(
            UserPrincipal principal,
            LocalDate date,
            String status,
            Long staffId,
            Long chairId,
            Boolean onlyLeaveConflict) {

        Long effectiveStaffId = staffId;
        if (principal != null && "DOCTOR".equals(principal.getRole())) {
            if (staffId != null && !staffId.equals(principal.getId())) {
                throw new BusinessException(
                        "ACCESS_DENIED_SCOPE",
                        "Bác sĩ chỉ được xem danh sách lịch hẹn thuộc về chính mình.",
                        HttpStatus.FORBIDDEN
                );
            }
            effectiveStaffId = principal.getId();
        }

        OffsetDateTime startOfDay = date != null
                ? date.atStartOfDay(CLINIC_ZONE).toOffsetDateTime()
                : OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime endOfDay = date != null
                ? date.plusDays(1).atStartOfDay(CLINIC_ZONE).toOffsetDateTime()
                : OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        String statusFilter = status != null ? status.trim() : "";
        long staffFilter = effectiveStaffId != null ? effectiveStaffId : -1L;
        long chairFilter = chairId != null ? chairId : -1L;
        Set<Long> conflictIds = leaveConflictService.findConflictingAppointmentIds();

        return appointmentRepository.searchStaffAppointments(statusFilter, staffFilter, chairFilter, startOfDay, endOfDay).stream()
                .filter(a -> !Boolean.TRUE.equals(onlyLeaveConflict) || conflictIds.contains(a.getAppointmentId()))
                .map(a -> toResponse(
                        a,
                        appointmentServiceRepository.findByAppointmentId(a.getAppointmentId()),
                        true,
                        conflictIds.contains(a.getAppointmentId()),
                        null))
                .toList();
    }

    /**
     * Lễ tân tạo lịch tại quầy (Màn L2 — UC-06, FR-06, FR-14):
     * - PHONE / SOCIAL -> CONFIRMED ngay (bỏ qua Blacklist)
     * - WALK_IN -> CHECKED_IN ngay (actual_arrival_at = now), hỗ trợ dịch vụ tạm (is_temporary = true, service_id = null)
     * - Hỗ trợ lịch cấp cứu ưu tiên (is_priority = true): tự tìm khung giờ trống gần nhất trong ngày nếu chưa chọn dateTime
     */
    @Transactional
    public AppointmentResponse createStaffAppointment(UserPrincipal principal, StaffCreateAppointmentRequest req) {
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        StaffAccount creator = staffAccountRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "Không tìm thấy thông tin nhân sự.", HttpStatus.NOT_FOUND));

        boolean isWalkIn = "WALK_IN".equals(req.getSource());
        boolean isPriority = Boolean.TRUE.equals(req.getIsPriority());

        boolean hasOfficialServices = req.getServiceIds() != null && !req.getServiceIds().isEmpty();
        boolean hasTempService = req.getTemporaryService() != null;
        if (!hasOfficialServices && !hasTempService) {
            throw new BusinessException("SERVICE_REQUIRED",
                    "Vui lòng chọn ít nhất một dịch vụ hoặc nhập dịch vụ tạm (temporaryService).",
                    HttpStatus.BAD_REQUEST);
        }

        // Lễ tân tạo hộ / vãng lai BỎ QUA kiểm tra Blacklist (BR Màn L2)
        Customer customer = customerRepository.findByPhone(req.getPhone().trim())
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .phone(req.getPhone().trim())
                        .isBlacklisted(false)
                        .createdAt(now)
                        .build()));

        ResolvedPatient resolvedPatient = resolveStaffPatientProfile(customer, req);
        PatientProfile patient = resolvedPatient.patient();
        boolean isNewPatient = resolvedPatient.isNewPatient();

        List<ServiceEntity> services = List.of();
        int serviceDuration = 0;
        if (hasOfficialServices) {
            services = serviceRepository.findByServiceIdInAndIsActiveTrue(req.getServiceIds());
            if (services.size() != new HashSet<>(req.getServiceIds()).size()) {
                throw new BusinessException("SERVICE_NOT_FOUND", "Có dịch vụ không tồn tại hoặc đã ngừng cung cấp.", HttpStatus.BAD_REQUEST);
            }
            serviceDuration += services.stream().mapToInt(ServiceEntity::getDuration).sum();
        }
        if (hasTempService) {
            serviceDuration += req.getTemporaryService().getDurationMinutes();
        }

        int chairBuffer = clinicConfigService.getChairBufferMinutes();
        int newPatientBuffer = isNewPatient ? clinicConfigService.getNewPatientBufferMinutes() : 0;
        short totalDuration = (short) (serviceDuration + chairBuffer + newPatientBuffer);

        OffsetDateTime slotStart;
        if (req.getDateTime() != null) {
            slotStart = req.getDateTime();
        } else if (isPriority) {
            slotStart = findEarliestAvailableSlotForPriority(req.getStaffId(), req.getChairId(), totalDuration, now);
        } else if (isWalkIn) {
            slotStart = now;
        } else {
            throw new BusinessException("DATETIME_REQUIRED", "Vui lòng chọn ngày giờ hẹn.", HttpStatus.BAD_REQUEST);
        }
        OffsetDateTime slotEnd = slotStart.plusMinutes(totalDuration);

        // Với WALK_IN hoặc cấp cứu (is_priority = true) không ép buộc phải có doctor_schedule định sẵn
        boolean requireShift = !(isWalkIn || isPriority);
        DoctorAssignment assignment = resolveDoctorForSlot(req.getStaffId(), slotStart, slotEnd, null, requireShift);
        DentalChair chair = resolveAvailableChair(req.getChairId(), slotStart, slotEnd, null);

        String targetStatus = isWalkIn ? "CHECKED_IN" : "CONFIRMED";
        String noteText = req.getNote();
        if (hasTempService && req.getTemporaryService().getDescription() != null && !req.getTemporaryService().getDescription().isBlank()) {
            String tempDesc = "[Dịch vụ tạm: " + req.getTemporaryService().getDescription().trim() + "]";
            noteText = (noteText == null || noteText.isBlank()) ? tempDesc : noteText + " " + tempDesc;
            if (noteText.length() > 500) {
                noteText = noteText.substring(0, 500);
            }
        }

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .staff(assignment.doctor())
                .chair(chair)
                .dateTime(slotStart)
                .duration(totalDuration)
                .status(targetStatus)
                .source(req.getSource())
                .bookedByPhone(customer.getPhone())
                .holdUntil(null)
                .actualArrivalAt(isWalkIn ? now : null)
                .isPriority(isPriority)
                .note(noteText)
                .createdAt(now)
                .createdBy(creator)
                .build();

        appointment = appointmentRepository.saveAndFlush(appointment);

        List<AppointmentServiceEntity> savedServices = new ArrayList<>();
        for (ServiceEntity svc : services) {
            savedServices.add(appointmentServiceRepository.save(AppointmentServiceEntity.builder()
                    .appointment(appointment)
                    .service(svc)
                    .isTemporary(false)
                    .durationSnapshot(svc.getDuration())
                    .priceSnapshot(svc.getPrice())
                    .isPerformed(false)
                    .build()));
        }
        if (hasTempService) {
            savedServices.add(appointmentServiceRepository.save(AppointmentServiceEntity.builder()
                    .appointment(appointment)
                    .service(null)
                    .isTemporary(true)
                    .durationSnapshot(req.getTemporaryService().getDurationMinutes())
                    .priceSnapshot(null)
                    .isPerformed(false)
                    .build()));
        }

        Map<String, Object> auditNew = new LinkedHashMap<>();
        auditNew.put("status", appointment.getStatus());
        auditNew.put("source", appointment.getSource());
        auditNew.put("isPriority", appointment.getIsPriority());
        auditNew.put("dateTime", appointment.getDateTime().toString());
        auditNew.put("staffId", assignment.doctor().getStaffId());
        auditNew.put("chairId", chair.getChairId());
        auditNew.put("hasTemporaryService", hasTempService);
        auditLogService.logStaff(creator.getStaffId(), "APPOINTMENT_CREATE_STAFF", "appointment",
                appointment.getAppointmentId(), null, auditNew);

        return toResponse(appointment, savedServices, true, false, customer.getIsBlacklisted());
    }

    /**
     * Lễ tân duyệt lịch PENDING -> CONFIRMED (Màn L1 — 5.3.5 mục 4a):
     * Nếu quá hạn hold_until -> tự động chuyển CANCELLED (SYSTEM) và commit, sau đó trả 409 APPT_HOLD_EXPIRED.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AppointmentResponse confirmAppointment(Long appointmentId, UserPrincipal principal, ConfirmAppointmentRequest req) {
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        if (!"PENDING".equals(appt.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Chỉ có thể duyệt lịch đang ở trạng thái PENDING (hiện tại: " + appt.getStatus() + ").",
                    HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        if (appt.getHoldUntil() != null && appt.getHoldUntil().isBefore(now)) {
            appt.setStatus("CANCELLED");
            appt.setCancelledByType("SYSTEM");
            appt.setCancelledBy(null);
            appt.setCancelReason("Hết hạn giữ chỗ");
            appointmentRepository.saveAndFlush(appt);

            auditLogService.logSystem(
                    "APPOINTMENT_AUTO_CANCEL",
                    "appointment",
                    appt.getAppointmentId(),
                    Map.of("status", "PENDING", "holdUntil", appt.getHoldUntil().toString()),
                    Map.of("status", "CANCELLED", "cancelledByType", "SYSTEM", "cancelReason", "Hết hạn giữ chỗ")
            );

            throw new BusinessException("APPT_HOLD_EXPIRED",
                    "Lịch chờ xác nhận đã quá hạn giữ chỗ (hold_until), hệ thống đã tự động chuyển sang CANCELLED.",
                    HttpStatus.CONFLICT);
        }

        if (req != null && req.getStaffId() != null) {
            StaffAccount newDoctor = staffAccountRepository.findById(req.getStaffId())
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

        // Xóa cờ auto-assigned sau khi Lễ tân đã chốt bác sĩ & xác nhận
        if (appt.getNote() != null && appt.getNote().contains(AUTO_ASSIGNED_TAG)) {
            appt.setNote(buildStoredNote(appt.getNote(), false));
        }

        appt.setStatus("CONFIRMED");
        appt = appointmentRepository.saveAndFlush(appt);

        auditLogService.logStaff(
                principal.getId(),
                "APPOINTMENT_CONFIRM",
                "appointment",
                appt.getAppointmentId(),
                Map.of("status", "PENDING"),
                Map.of("status", "CONFIRMED", "staffId", appt.getStaff().getStaffId(), "chairId", appt.getChair().getChairId())
        );

        List<AppointmentServiceEntity> services = appointmentServiceRepository.findByAppointmentId(appt.getAppointmentId());
        return toResponse(appt, services, true, false, null);
    }

    /**
     * Lễ tân đổi ngày giờ / bác sĩ / ghế tự do (Màn L3 — UC-08, UC-10, FR-10, FR-13).
     */
    @Transactional
    public AppointmentResponse staffRescheduleAppointment(Long appointmentId, UserPrincipal principal, StaffRescheduleRequest req) {
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        if (!"PENDING".equals(appt.getStatus()) && !"CONFIRMED".equals(appt.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Chỉ có thể đổi lịch ở trạng thái PENDING hoặc CONFIRMED (hiện tại: " + appt.getStatus() + ").",
                    HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime newStart = req.getDateTime();
        OffsetDateTime newEnd = newStart.plusMinutes(appt.getDuration());

        Map<String, Object> oldSnapshot = Map.of(
                "dateTime", appt.getDateTime().toString(),
                "staffId", appt.getStaff().getStaffId(),
                "chairId", appt.getChair().getChairId(),
                "status", appt.getStatus()
        );

        Long targetStaffId = req.getStaffId() != null ? req.getStaffId() : appt.getStaff().getStaffId();
        DoctorAssignment assignment = resolveDoctorForSlot(targetStaffId, newStart, newEnd, appt.getAppointmentId(), false);
        DentalChair targetChair = resolveAvailableChair(req.getChairId(), newStart, newEnd, appt.getAppointmentId());

        appt.setDateTime(newStart);
        appt.setStaff(assignment.doctor());
        appt.setChair(targetChair);
        if (req.getNote() != null) {
            appt.setNote(req.getNote().trim().isEmpty() ? null : req.getNote().trim());
        }
        if ("PENDING".equals(appt.getStatus())) {
            appt.setHoldUntil(calculateHoldUntil(OffsetDateTime.now(CLINIC_ZONE), newStart));
        }

        appt = appointmentRepository.saveAndFlush(appt);

        Map<String, Object> newSnapshot = Map.of(
                "dateTime", appt.getDateTime().toString(),
                "staffId", appt.getStaff().getStaffId(),
                "chairId", appt.getChair().getChairId(),
                "status", appt.getStatus()
        );
        auditLogService.logStaff(principal.getId(), "APPOINTMENT_RESCHEDULE", "appointment",
                appt.getAppointmentId(), oldSnapshot, newSnapshot);

        boolean hasConflict = leaveConflictService.findConflictingAppointmentIds().contains(appt.getAppointmentId());
        return toResponse(appt, appointmentServiceRepository.findByAppointmentId(appt.getAppointmentId()), true, hasConflict, null);
    }

    /**
     * Lễ tân Check-in khách Đã đến (Màn L3 — UC-09, FR-11).
     */
    @Transactional
    public AppointmentResponse checkInAppointment(Long appointmentId, UserPrincipal principal) {
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        if (!"CONFIRMED".equals(appt.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Chỉ có thể Check-in lịch hẹn đang ở trạng thái CONFIRMED (hiện tại: " + appt.getStatus() + ").",
                    HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        appt.setStatus("CHECKED_IN");
        appt.setActualArrivalAt(now);
        appt = appointmentRepository.saveAndFlush(appt);

        auditLogService.logStaff(
                principal.getId(),
                "APPOINTMENT_CHECK_IN",
                "appointment",
                appt.getAppointmentId(),
                Map.of("status", "CONFIRMED"),
                Map.of("status", "CHECKED_IN", "actualArrivalAt", now.toString())
        );

        return toResponse(appt, appointmentServiceRepository.findByAppointmentId(appt.getAppointmentId()), true, false, null);
    }

    @Transactional
    public AppointmentResponse staffCancelAppointment(Long appointmentId, UserPrincipal principal, CancelAppointmentRequest req) {
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        if ("COMPLETED".equals(appt.getStatus()) || "CANCELLED".equals(appt.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Không thể hủy lịch ở trạng thái " + appt.getStatus(), HttpStatus.BAD_REQUEST);
        }

        StaffAccount staff = staffAccountRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "Không tìm thấy thông tin nhân sự.", HttpStatus.NOT_FOUND));

        String oldStatus = appt.getStatus();
        appt.setStatus("CANCELLED");
        appt.setCancelledByType("STAFF");
        appt.setCancelledBy(staff);
        appt.setCancelReason(req.getCancelReason().trim());
        appt = appointmentRepository.saveAndFlush(appt);

        auditLogService.logStaff(
                principal.getId(),
                "APPOINTMENT_CANCEL",
                "appointment",
                appt.getAppointmentId(),
                Map.of("status", oldStatus),
                Map.of("status", "CANCELLED", "cancelledByType", "STAFF", "cancelReason", appt.getCancelReason())
        );

        return toResponse(appt, appointmentServiceRepository.findByAppointmentId(appt.getAppointmentId()), true, false, null);
    }

    /**
     * Lễ tân đánh dấu Không đến (Màn L3 — UC-09, UC-11, FR-15):
     * Ghi bản ghi no_show_event; Trigger trg_no_show_blacklist tự động cập nhật customer.is_blacklisted.
     */
    @Transactional
    public AppointmentResponse markNoShow(Long appointmentId, UserPrincipal principal) {
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        if (!"CONFIRMED".equals(appt.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Chỉ có thể đánh dấu Không đến cho lịch hẹn đang ở trạng thái CONFIRMED (hiện tại: " + appt.getStatus() + ").",
                    HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        int lateMinutes = clinicConfigService.getLateNoshowMinutes();
        if (now.isBefore(appt.getDateTime().plusMinutes(lateMinutes))) {
            throw new BusinessException(
                    "NO_SHOW_TOO_EARLY",
                    "Chỉ có thể đánh dấu Không đến khi khách đã trễ ít nhất " + lateMinutes + " phút so với giờ hẹn.",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        appt.setStatus("NO_SHOW");
        appt = appointmentRepository.saveAndFlush(appt);

        noShowEventRepository.saveAndFlush(NoShowEvent.builder()
                .patient(appt.getPatient())
                .appointment(appt)
                .occurredAt(now)
                .isReversed(false)
                .build());

        Long customerId = appt.getPatient().getCustomer().getCustomerId();
        Boolean latestBlacklisted = customerRepository.findLatestBlacklistedStatus(customerId);

        auditLogService.logStaff(
                principal.getId(),
                "APPOINTMENT_NO_SHOW",
                "appointment",
                appt.getAppointmentId(),
                Map.of("status", "CONFIRMED"),
                Map.of("status", "NO_SHOW", "customerBlacklisted", Boolean.TRUE.equals(latestBlacklisted))
        );

        return toResponse(appt, appointmentServiceRepository.findByAppointmentId(appt.getAppointmentId()), true, false, latestBlacklisted);
    }

    /**
     * Lễ tân hoàn tác trạng thái Không đến trong cùng ngày khám (Màn L3 — 5.3.5 mục 4b):
     * Cập nhật no_show_event.is_reversed = true (Trigger tự tính lại Blacklist), chuyển lịch sang CHECKED_IN.
     */
    @Transactional
    public AppointmentResponse reverseNoShow(Long appointmentId, UserPrincipal principal, ReverseNoShowRequest req) {
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new BusinessException("APPT_NOT_FOUND", "Không tìm thấy lịch hẹn #" + appointmentId, HttpStatus.NOT_FOUND));

        if (!"NO_SHOW".equals(appt.getStatus())) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Chỉ có thể hoàn tác cho lịch hẹn đang ở trạng thái NO_SHOW (hiện tại: " + appt.getStatus() + ").",
                    HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        LocalDate apptDay = appt.getDateTime().atZoneSameInstant(CLINIC_ZONE).toLocalDate();
        LocalDate today = now.toLocalDate();
        if (!apptDay.equals(today)) {
            throw new BusinessException("REVERSE_NO_SHOW_EXPIRED",
                    "Chỉ được phép hoàn tác trạng thái Không đến trong cùng ngày khám (" + apptDay + ").",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        StaffAccount staff = staffAccountRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "Không tìm thấy thông tin nhân sự.", HttpStatus.NOT_FOUND));

        NoShowEvent event = noShowEventRepository.findByAppointmentAppointmentId(appointmentId)
                .orElseThrow(() -> new BusinessException("NOSHOW_EVENT_NOT_FOUND",
                        "Không tìm thấy bản ghi no_show_event cho lịch hẹn #" + appointmentId,
                        HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(event.getIsReversed())) {
            throw new BusinessException("NOSHOW_ALREADY_REVERSED", "Sự kiện Không đến này đã được hoàn tác trước đó.", HttpStatus.BAD_REQUEST);
        }

        event.setIsReversed(true);
        event.setReversedBy(staff);
        event.setReversedAt(now);
        event.setReversedReason(req.getReversedReason().trim());
        noShowEventRepository.saveAndFlush(event);

        appt.setStatus("CHECKED_IN");
        appt.setActualArrivalAt(now);
        appt = appointmentRepository.saveAndFlush(appt);

        Long customerId = appt.getPatient().getCustomer().getCustomerId();
        Boolean latestBlacklisted = customerRepository.findLatestBlacklistedStatus(customerId);

        auditLogService.logStaff(
                principal.getId(),
                "APPOINTMENT_REVERSE_NO_SHOW",
                "appointment",
                appt.getAppointmentId(),
                Map.of("status", "NO_SHOW"),
                Map.of("status", "CHECKED_IN", "reversedReason", event.getReversedReason(),
                        "customerBlacklisted", Boolean.TRUE.equals(latestBlacklisted))
        );

        return toResponse(appt, appointmentServiceRepository.findByAppointmentId(appt.getAppointmentId()), true, false, latestBlacklisted);
    }

    // =========================================================================
    // 3. HELPER & BUSINESS RULES
    // =========================================================================

    /**
     * Công thức tính Hold_Until theo mục 3.1.7, 5.2 và 5.3.5 (Mục 3):
     * 1) base = min(created_at + 12h, date_time - 1h)
     * 2) Nếu rơi ngoài giờ làm việc phòng khám [open, close) hoặc rơi ngày nghỉ lễ:
     *    đẩy tới đầu giờ làm việc kế tiếp (open) của ngày làm việc tiếp theo.
     * 3) Chặn trên tại date_time - 30 phút (và không vượt quá date_time theo ck_appt_hold_before_start).
     */
    public OffsetDateTime calculateHoldUntil(OffsetDateTime createdAt, OffsetDateTime dateTime) {
        LocalTime openTime = clinicConfigService.getClinicOpenTime();
        LocalTime closeTime = clinicConfigService.getClinicCloseTime();

        ZonedDateTime createdZdt = createdAt.atZoneSameInstant(CLINIC_ZONE);
        ZonedDateTime apptZdt = dateTime.atZoneSameInstant(CLINIC_ZONE);

        ZonedDateTime opt1 = createdZdt.plusHours(12);
        ZonedDateTime opt2 = apptZdt.minusHours(1);
        ZonedDateTime candidate = opt1.isBefore(opt2) ? opt1 : opt2;

        candidate = pushToWorkingHours(candidate, openTime, closeTime);

        ZonedDateTime upperBound = apptZdt.minusMinutes(30);
        if (candidate.isAfter(upperBound)) {
            candidate = upperBound;
        }
        if (candidate.isAfter(apptZdt)) {
            candidate = apptZdt;
        }
        return candidate.toOffsetDateTime();
    }

    private ZonedDateTime pushToWorkingHours(ZonedDateTime dt, LocalTime openTime, LocalTime closeTime) {
        LocalDate date = dt.toLocalDate();
        LocalTime time = dt.toLocalTime();

        boolean isHoliday = clinicHolidayRepository.existsByHolidayDate(date);
        if (!isHoliday && !time.isBefore(openTime) && time.isBefore(closeTime)) {
            return dt;
        }

        LocalDate targetDate = (!isHoliday && time.isBefore(openTime)) ? date : date.plusDays(1);
        int guard = 0;
        while (clinicHolidayRepository.existsByHolidayDate(targetDate) && guard < 30) {
            targetDate = targetDate.plusDays(1);
            guard++;
        }
        return ZonedDateTime.of(targetDate, openTime, CLINIC_ZONE);
    }

    private void validateCustomerBookingWindow(OffsetDateTime dateTime, OffsetDateTime now) {
        int minLeadHours = clinicConfigService.getBookingMinLeadHours();
        int maxLeadDays = clinicConfigService.getBookingMaxLeadDays();
        OffsetDateTime minTime = now.plusHours(minLeadHours);
        OffsetDateTime maxTime = now.plusDays(maxLeadDays);

        if (dateTime.isBefore(minTime) || dateTime.isAfter(maxTime)) {
            throw new BusinessException(
                    "APPT_BOOKING_WINDOW_VIOLATION",
                    String.format("Lịch hẹn trực tuyến phải đặt trước tối thiểu %d giờ và tối đa %d ngày.", minLeadHours, maxLeadDays),
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    private void validateCustomerChangeWindow(OffsetDateTime apptDateTime, OffsetDateTime now, String actionLabel) {
        int minHours = clinicConfigService.getCustomerChangeMinHours();
        if (now.isAfter(apptDateTime.minusHours(minHours))) {
            throw new BusinessException(
                    "APPT_CHANGE_WINDOW_VIOLATION",
                    String.format("Không thể tự %s khi còn dưới %d giờ trước giờ hẹn. Vui lòng liên hệ hotline phòng khám.", actionLabel, minHours),
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    private void validateWithinClinicHours(OffsetDateTime start, OffsetDateTime end) {
        LocalTime open = clinicConfigService.getClinicOpenTime();
        LocalTime close = clinicConfigService.getClinicCloseTime();
        ZonedDateTime startZ = start.atZoneSameInstant(CLINIC_ZONE);
        ZonedDateTime endZ = end.atZoneSameInstant(CLINIC_ZONE);

        if (!startZ.toLocalDate().equals(endZ.toLocalDate())
                || startZ.toLocalTime().isBefore(open)
                || endZ.toLocalTime().isAfter(close)) {
            throw new BusinessException(
                    "OUTSIDE_CLINIC_HOURS",
                    String.format("Khung giờ đặt lịch phải nằm trong giờ làm việc phòng khám (%s - %s).", open, close),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void verifyCustomerOwnership(Appointment appt, UserPrincipal principal) {
        Long ownerCustomerId = appt.getPatient().getCustomer().getCustomerId();
        if (!ownerCustomerId.equals(principal.getId())) {
            throw new BusinessException("ACCESS_DENIED_SCOPE",
                    "Bạn không có quyền truy cập lịch hẹn của khách hàng khác.",
                    HttpStatus.FORBIDDEN);
        }
    }

    private boolean matchesCustomerTab(Appointment a, String tab, OffsetDateTime now) {
        if (tab == null || tab.isBlank() || "ALL".equalsIgnoreCase(tab)) {
            return true;
        }
        boolean isUpcoming = ("PENDING".equals(a.getStatus()) || "CONFIRMED".equals(a.getStatus()) || "CHECKED_IN".equals(a.getStatus()))
                && !a.getDateTime().isBefore(now.minusHours(2));
        if ("UPCOMING".equalsIgnoreCase(tab)) {
            return isUpcoming;
        }
        if ("PAST".equalsIgnoreCase(tab)) {
            return !isUpcoming;
        }
        return true;
    }

    private ResolvedPatient resolveCustomerPatientProfile(Customer customer, CreateAppointmentRequest req, OffsetDateTime now) {
        if (req.getPatientId() != null) {
            PatientProfile existing = patientProfileRepository
                    .findByPatientIdAndCustomerCustomerId(req.getPatientId(), customer.getCustomerId())
                    .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND",
                            "Không tìm thấy hồ sơ bệnh nhân thuộc tài khoản của bạn.",
                            HttpStatus.NOT_FOUND));
            return new ResolvedPatient(existing, false);
        }

        String fullName = req.getPatientInfo() != null ? req.getPatientInfo().getFullName() : req.getPatientFullName();
        Short birthYear = req.getPatientInfo() != null ? req.getPatientInfo().getBirthYear() : req.getBirthYear();
        String gender = req.getPatientInfo() != null ? req.getPatientInfo().getGender() : req.getGender();
        String address = req.getPatientInfo() != null ? req.getPatientInfo().getAddress() : null;

        if (fullName == null || fullName.isBlank() || birthYear == null) {
            throw new BusinessException("PATIENT_INFO_REQUIRED",
                    "Vui lòng chọn patientId hoặc cung cấp thông tin họ tên & năm sinh để tạo hồ sơ bệnh nhân mới.",
                    HttpStatus.BAD_REQUEST);
        }

        PatientProfile created = patientProfileRepository.save(PatientProfile.builder()
                .customer(customer)
                .fullName(fullName.trim())
                .birthYear(birthYear)
                .gender(gender)
                .address(address)
                .build());

        if ("RELATIVE".equalsIgnoreCase(req.getBookingFor()) || req.getPatientInfo() != null) {
            String relation = req.getPatientInfo() != null ? req.getPatientInfo().getRelation() : null;
            boolean isGuardian = req.getPatientInfo() != null && Boolean.TRUE.equals(req.getPatientInfo().getIsGuardian());
            consentRepository.save(Consent.builder()
                    .phone(customer.getPhone())
                    .customer(customer)
                    .consentTime(now)
                    .channel("WEB_PORTAL")
                    .action("GIVEN")
                    .policyVersion("v1.0-2026")
                    .consentedBy("Khách hàng " + customer.getPhone())
                    .forPatient(created)
                    .relation(relation != null ? relation : ("RELATIVE".equalsIgnoreCase(req.getBookingFor()) ? "RELATIVE" : "SELF"))
                    .isGuardian(isGuardian)
                    .build());
        }

        return new ResolvedPatient(created, true);
    }

    private ResolvedPatient resolveStaffPatientProfile(Customer customer, StaffCreateAppointmentRequest req) {
        if (req.getPatientId() != null) {
            PatientProfile existing = patientProfileRepository.findById(req.getPatientId())
                    .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND",
                            "Không tìm thấy hồ sơ bệnh nhân #" + req.getPatientId(),
                            HttpStatus.NOT_FOUND));
            return new ResolvedPatient(existing, false);
        }

        if (req.getPatientInfo() != null) {
            PatientProfile created = patientProfileRepository.save(PatientProfile.builder()
                    .customer(customer)
                    .fullName(req.getPatientInfo().getFullName().trim())
                    .birthYear(req.getPatientInfo().getBirthYear())
                    .gender(req.getPatientInfo().getGender())
                    .address(req.getPatientInfo().getAddress())
                    .build());
            return new ResolvedPatient(created, true);
        }

        List<PatientProfile> existingProfiles = patientProfileRepository.findByCustomerCustomerId(customer.getCustomerId());
        if (!existingProfiles.isEmpty()) {
            return new ResolvedPatient(existingProfiles.get(0), false);
        }

        throw new BusinessException("PATIENT_INFO_REQUIRED",
                "Khách hàng chưa có hồ sơ bệnh nhân. Vui lòng cung cấp patientInfo (fullName, birthYear).",
                HttpStatus.BAD_REQUEST);
    }

    private OffsetDateTime findEarliestAvailableSlotForPriority(
            Long requestedStaffId,
            Long requestedChairId,
            short totalDuration,
            OffsetDateTime now) {

        LocalTime openTime = clinicConfigService.getClinicOpenTime();
        LocalTime closeTime = clinicConfigService.getClinicCloseTime();
        ZonedDateTime nowZoned = now.atZoneSameInstant(CLINIC_ZONE).truncatedTo(ChronoUnit.MINUTES);
        int rem = nowZoned.getMinute() % 15;
        ZonedDateTime roundedNow = rem == 0 ? nowZoned : nowZoned.plusMinutes(15 - rem);

        // Tìm khung giờ trống gần nhất trong ngày hôm nay trước; nếu đã quá giờ đóng cửa thì tìm tiếp các ngày kế tiếp
        for (int dayOffset = 0; dayOffset <= 3; dayOffset++) {
            LocalDate date = nowZoned.toLocalDate().plusDays(dayOffset);
            LocalTime startCandidate = dayOffset == 0
                    ? (roundedNow.toLocalTime().isBefore(openTime) ? openTime : roundedNow.toLocalTime())
                    : openTime;
            LocalTime latestStart = closeTime.minusMinutes(totalDuration);
            if (startCandidate.isAfter(latestStart)) {
                continue;
            }

            OffsetDateTime dayStart = date.atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
            OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
            List<Appointment> dayAppts = appointmentRepository.findActiveAppointmentsInRange(dayStart, dayEnd);
            List<DentalChair> chairs = requestedChairId != null
                    ? dentalChairRepository.findById(requestedChairId).filter(c -> "ACTIVE".equals(c.getStatus())).map(List::of).orElse(List.of())
                    : dentalChairRepository.findByStatus("ACTIVE");
            List<StaffAccount> doctors = requestedStaffId != null
                    ? staffAccountRepository.findById(requestedStaffId)
                            .filter(s -> Boolean.TRUE.equals(s.getStatus()) && "DOCTOR".equals(s.getRole().getRoleName()))
                            .map(List::of).orElse(List.of())
                    : staffAccountRepository.findByRoleRoleNameAndStatusTrue("DOCTOR");

            LocalTime cursor = startCandidate;
            while (!cursor.isAfter(latestStart)) {
                OffsetDateTime slotStart = ZonedDateTime.of(date, cursor, CLINIC_ZONE).toOffsetDateTime();
                OffsetDateTime slotEnd = slotStart.plusMinutes(totalDuration);
                LocalTime slotStartLocal = cursor;
                LocalTime slotEndLocal = cursor.plusMinutes(totalDuration);

                boolean hasFreeChair = chairs.stream().anyMatch(c -> dayAppts.stream()
                        .filter(a -> a.getChair().getChairId().equals(c.getChairId()))
                        .noneMatch(a -> slotStart.isBefore(getEffectiveEndTime(a)) && slotEnd.isAfter(a.getDateTime())));

                boolean hasFreeDoctor = doctors.stream()
                        .filter(d -> !isDoctorOnLeave(d.getStaffId(), date, slotStartLocal, slotEndLocal))
                        .anyMatch(d -> dayAppts.stream()
                                .filter(a -> a.getStaff().getStaffId().equals(d.getStaffId()))
                                .noneMatch(a -> slotStart.isBefore(getEffectiveEndTime(a)) && slotEnd.isAfter(a.getDateTime())));

                if (hasFreeChair && hasFreeDoctor) {
                    return slotStart;
                }
                cursor = cursor.plusMinutes(15);
            }
        }

        throw new BusinessException(
                "APPT_SLOT_CONFLICT",
                "Không còn khung giờ trống khả dụng cho ca cấp cứu (không tự động hủy/dời lịch đã xác nhận của khách khác).",
                HttpStatus.CONFLICT
        );
    }

    private DoctorAssignment resolveDoctorForSlot(
            Long requestedStaffId,
            OffsetDateTime start,
            OffsetDateTime end,
            Long excludeAppointmentId,
            boolean requireScheduledShift) {

        LocalDate date = start.atZoneSameInstant(CLINIC_ZONE).toLocalDate();
        LocalTime startTime = start.atZoneSameInstant(CLINIC_ZONE).toLocalTime();
        LocalTime endTime = end.atZoneSameInstant(CLINIC_ZONE).toLocalTime();

        OffsetDateTime dayStart = date.atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        List<Appointment> dayAppts = appointmentRepository.findActiveAppointmentsInRange(dayStart, dayEnd).stream()
                .filter(a -> excludeAppointmentId == null || !a.getAppointmentId().equals(excludeAppointmentId))
                .toList();

        if (requestedStaffId != null) {
            StaffAccount doctor = staffAccountRepository.findById(requestedStaffId)
                    .filter(s -> Boolean.TRUE.equals(s.getStatus()) && "DOCTOR".equals(s.getRole().getRoleName()))
                    .orElseThrow(() -> new BusinessException("DOCTOR_NOT_FOUND", "Bác sĩ không tồn tại hoặc đang ngừng hoạt động.", HttpStatus.NOT_FOUND));

            if (requireScheduledShift) {
                boolean hasShift = doctorScheduleRepository
                        .findByStaffStaffIdAndShiftDateAndStatus(doctor.getStaffId(), date, "ACTIVE")
                        .stream()
                        .anyMatch(sc -> !startTime.isBefore(sc.getStartTime()) && !endTime.isAfter(sc.getEndTime()));
                if (!hasShift) {
                    throw new BusinessException("DOCTOR_NOT_SCHEDULED",
                            "Bác sĩ không có ca làm việc trong khung giờ này.",
                            HttpStatus.CONFLICT);
                }
            }

            checkDoctorLeave(doctor.getStaffId(), start, end);

            boolean busy = dayAppts.stream()
                    .filter(a -> a.getStaff().getStaffId().equals(doctor.getStaffId()))
                    .anyMatch(a -> start.isBefore(getEffectiveEndTime(a)) && end.isAfter(a.getDateTime()));
            if (busy) {
                throw new BusinessException("APPT_SLOT_CONFLICT",
                        "Khung giờ vừa được người khác giữ. Vui lòng chọn giờ khác.",
                        HttpStatus.CONFLICT);
            }
            return new DoctorAssignment(doctor, false);
        }

        // Auto-Assignment: ưu tiên bác sĩ có ca ACTIVE; nếu không yêu cầu ca (WALK_IN/cấp cứu) xét toàn bộ bác sĩ đang hoạt động
        List<StaffAccount> pool;
        if (requireScheduledShift) {
            pool = doctorScheduleRepository.findByShiftDateAndStatus(date, "ACTIVE").stream()
                    .filter(sc -> !startTime.isBefore(sc.getStartTime()) && !endTime.isAfter(sc.getEndTime()))
                    .map(DoctorSchedule::getStaff)
                    .filter(s -> Boolean.TRUE.equals(s.getStatus()) && "DOCTOR".equals(s.getRole().getRoleName()))
                    .toList();
        } else {
            pool = staffAccountRepository.findByRoleRoleNameAndStatusTrue("DOCTOR");
        }

        List<StaffAccount> candidates = pool.stream()
                .filter(doc -> !isDoctorOnLeave(doc.getStaffId(), date, startTime, endTime))
                .filter(doc -> dayAppts.stream()
                        .filter(a -> a.getStaff().getStaffId().equals(doc.getStaffId()))
                        .noneMatch(a -> start.isBefore(getEffectiveEndTime(a)) && end.isAfter(a.getDateTime())))
                .sorted(Comparator.comparingLong((StaffAccount d) ->
                        dayAppts.stream().filter(a -> a.getStaff().getStaffId().equals(d.getStaffId())).count())
                        .thenComparing(StaffAccount::getStaffId))
                .toList();

        if (candidates.isEmpty()) {
            throw new BusinessException("APPT_SLOT_CONFLICT",
                    "Không còn bác sĩ trống trong khung giờ này. Vui lòng chọn giờ khác.",
                    HttpStatus.CONFLICT);
        }
        return new DoctorAssignment(candidates.get(0), true);
    }

    private void checkDoctorLeave(Long doctorId, OffsetDateTime start, OffsetDateTime end) {
        LocalDate date = start.atZoneSameInstant(CLINIC_ZONE).toLocalDate();
        LocalTime startTime = start.atZoneSameInstant(CLINIC_ZONE).toLocalTime();
        LocalTime endTime = end.atZoneSameInstant(CLINIC_ZONE).toLocalTime();

        if (isDoctorOnLeave(doctorId, date, startTime, endTime)) {
            throw new BusinessException("APPT_DOCTOR_ON_LEAVE",
                    "Bác sĩ phụ trách có lịch nghỉ đột xuất trong khung giờ này.",
                    HttpStatus.CONFLICT);
        }
    }

    private boolean isDoctorOnLeave(Long doctorId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        return doctorLeaveRepository
                .findByStaffStaffIdAndLeaveDateAndStatus(doctorId, date, "APPROVED")
                .stream()
                .anyMatch(l -> startTime.isBefore(l.getEndTime()) && endTime.isAfter(l.getStartTime()));
    }

    private DentalChair resolveAvailableChair(Long chairId, OffsetDateTime start, OffsetDateTime end, Long excludeAppointmentId) {
        LocalDate date = start.atZoneSameInstant(CLINIC_ZONE).toLocalDate();
        OffsetDateTime dayStart = date.atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        List<Appointment> dayAppts = appointmentRepository.findActiveAppointmentsInRange(dayStart, dayEnd).stream()
                .filter(a -> excludeAppointmentId == null || !a.getAppointmentId().equals(excludeAppointmentId))
                .toList();

        if (chairId != null) {
            DentalChair chair = dentalChairRepository.findById(chairId)
                    .filter(c -> "ACTIVE".equals(c.getStatus()))
                    .orElseThrow(() -> new BusinessException("CHAIR_NOT_FOUND", "Ghế nha không tồn tại hoặc đang bảo trì.", HttpStatus.NOT_FOUND));
            boolean busy = dayAppts.stream()
                    .filter(a -> a.getChair().getChairId().equals(chair.getChairId()))
                    .anyMatch(a -> start.isBefore(getEffectiveEndTime(a)) && end.isAfter(a.getDateTime()));
            if (busy) {
                throw new BusinessException("APPT_SLOT_CONFLICT", "Ghế nha đã kín trong khung giờ này.", HttpStatus.CONFLICT);
            }
            return chair;
        }

        List<DentalChair> activeChairs = dentalChairRepository.findByStatus("ACTIVE");
        if (activeChairs.isEmpty()) {
            throw new BusinessException("NO_ACTIVE_CHAIR", "Không có ghế nha khả dụng.", HttpStatus.CONFLICT);
        }

        for (DentalChair chair : activeChairs) {
            boolean busy = dayAppts.stream()
                    .filter(a -> a.getChair().getChairId().equals(chair.getChairId()))
                    .anyMatch(a -> start.isBefore(getEffectiveEndTime(a)) && end.isAfter(a.getDateTime()));
            if (!busy) {
                return chair;
            }
        }
        throw new BusinessException("APPT_SLOT_CONFLICT", "Tất cả ghế nha đã kín trong khung giờ này.", HttpStatus.CONFLICT);
    }

    private OffsetDateTime getEffectiveEndTime(Appointment a) {
        return a.getEndTime() != null ? a.getEndTime() : a.getDateTime().plusMinutes(a.getDuration());
    }

    private String buildStoredNote(String note, boolean autoAssigned) {
        String cleaned = note == null ? "" : note.replace(AUTO_ASSIGNED_TAG, "").trim();
        if (!autoAssigned) {
            return cleaned.isEmpty() ? null : cleaned;
        }
        String combined = cleaned.isEmpty() ? AUTO_ASSIGNED_TAG : AUTO_ASSIGNED_TAG + " " + cleaned;
        return combined.length() > 500 ? combined.substring(0, 500) : combined;
    }

    private String formatAppointmentCode(Appointment a) {
        int year = a.getDateTime().atZoneSameInstant(CLINIC_ZONE).getYear();
        return String.format("LH-%d-%06d", year, a.getAppointmentId());
    }

    private String toStatusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "Chờ xác nhận";
            case "CONFIRMED" -> "Đã xác nhận";
            case "CHECKED_IN" -> "Đã đến";
            case "COMPLETED" -> "Hoàn tất";
            case "CANCELLED" -> "Đã hủy";
            case "NO_SHOW" -> "Không đến";
            default -> status;
        };
    }

    private AppointmentResponse toResponse(
            Appointment a,
            List<AppointmentServiceEntity> services,
            boolean includeChairInfo,
            boolean hasLeaveConflict,
            Boolean customerBlacklisted) {

        OffsetDateTime computedEnd = getEffectiveEndTime(a);
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        int changeMinHours = clinicConfigService.getCustomerChangeMinHours();
        boolean canModify = ("PENDING".equals(a.getStatus()) || "CONFIRMED".equals(a.getStatus()))
                && !now.isAfter(a.getDateTime().minusHours(changeMinHours));
        boolean autoAssigned = a.getNote() != null && a.getNote().contains(AUTO_ASSIGNED_TAG);
        String displayNote = a.getNote() != null ? a.getNote().replace(AUTO_ASSIGNED_TAG, "").trim() : null;
        if (displayNote != null && displayNote.isEmpty()) {
            displayNote = null;
        }

        return AppointmentResponse.builder()
                .appointmentId(a.getAppointmentId())
                .appointmentCode(formatAppointmentCode(a))
                .patientId(a.getPatient().getPatientId())
                .patientName(a.getPatient().getFullName())
                .bookedByPhone(a.getBookedByPhone())
                .doctorId(a.getStaff().getStaffId())
                .doctorName(a.getStaff().getFullName())
                .doctorAutoAssigned(autoAssigned)
                .chairId(includeChairInfo ? a.getChair().getChairId() : null)
                .chairName(includeChairInfo ? a.getChair().getChairName() : null)
                .dateTime(a.getDateTime())
                .duration(a.getDuration())
                .endTime(computedEnd)
                .status(a.getStatus())
                .statusLabel(toStatusLabel(a.getStatus()))
                .source(a.getSource())
                .holdUntil(a.getHoldUntil())
                .actualArrivalAt(a.getActualArrivalAt())
                .canModify(canModify)
                .isPriority(a.getIsPriority())
                .hasLeaveConflict(includeChairInfo ? hasLeaveConflict : null)
                .customerBlacklisted(customerBlacklisted)
                .cancelReason(a.getCancelReason())
                .note(displayNote)
                .createdAt(a.getCreatedAt())
                .services(services.stream()
                        .map(s -> ServiceItemDto.builder()
                                .serviceId(s.getService() != null ? s.getService().getServiceId() : null)
                                .serviceName(s.getService() != null ? s.getService().getName() : "Dịch vụ tạm")
                                .isTemporary(s.getIsTemporary())
                                .durationSnapshot(s.getDurationSnapshot())
                                .priceSnapshot(s.getPriceSnapshot())
                                .build())
                        .toList())
                .build();
    }

    private record ResolvedPatient(PatientProfile patient, boolean isNewPatient) {}

    private record DoctorAssignment(StaffAccount doctor, boolean autoAssigned) {}

    public record IcsDownloadPayload(String filename, String content) {}
}
