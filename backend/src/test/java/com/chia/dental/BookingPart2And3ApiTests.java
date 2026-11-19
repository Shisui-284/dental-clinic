package com.chia.dental;

import com.chia.dental.entity.*;
import com.chia.dental.repository.*;
import com.chia.dental.scheduler.HoldExpirationJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.scheduling.enabled=false")
@AutoConfigureMockMvc
@Transactional
class BookingPart2And3ApiTests {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private DentalChairRepository dentalChairRepository;

    @Autowired
    private DoctorScheduleRepository doctorScheduleRepository;

    @Autowired
    private NoShowEventRepository noShowEventRepository;

    @Autowired
    private HoldExpirationJob holdExpirationJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String loginStaff(String username) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "123456"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private void ensureDoctorShift(long doctorId, LocalDate date) {
        if (doctorScheduleRepository.findByStaffStaffIdAndShiftDateAndStatus(doctorId, date, "ACTIVE").isEmpty()) {
            StaffAccount doc = staffAccountRepository.findById(doctorId).orElseThrow();
            doctorScheduleRepository.save(DoctorSchedule.builder()
                    .staff(doc)
                    .shiftDate(date)
                    .startTime(LocalTime.of(8, 0))
                    .endTime(LocalTime.of(17, 0))
                    .status("ACTIVE")
                    .build());
        }
    }

    @Test
    void testReceptionistCreateCounterBookings_Reschedule_AndCheckIn() throws Exception {
        String receptionistToken = loginStaff("letan01");
        LocalDate futureDay = LocalDate.now(CLINIC_ZONE).plusDays(7);
        ensureDoctorShift(4L, futureDay);
        ensureDoctorShift(5L, futureDay);

        // 1. L2: Đặt hộ qua PHONE cho khách đang bị Blacklist (0900000003) -> BỎ QUA Blacklist, tạo CONFIRMED
        OffsetDateTime phoneApptTime = ZonedDateTime.of(futureDay, LocalTime.of(9, 0), CLINIC_ZONE).toOffsetDateTime();
        MvcResult phoneRes = mockMvc.perform(post("/api/v1/staff/appointments")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "PHONE",
                                  "phone": "0900000003",
                                  "patientId": 5,
                                  "staffId": 4,
                                  "dateTime": "%s",
                                  "serviceIds": [1],
                                  "note": "Khách gọi hotline đặt hộ"
                                }
                                """.formatted(phoneApptTime)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.source").value("PHONE"))
                .andExpect(jsonPath("$.data.customerBlacklisted").value(true))
                .andReturn();

        long phoneApptId = objectMapper.readTree(phoneRes.getResponse().getContentAsString())
                .path("data").path("appointmentId").asLong();

        // 2. L2: Khách vãng lai WALK_IN + dịch vụ tạm (is_temporary = true, service_id = null) + cấp cứu (is_priority = true)
        OffsetDateTime walkInTime = ZonedDateTime.of(futureDay, LocalTime.of(15, 0), CLINIC_ZONE).toOffsetDateTime();
        mockMvc.perform(post("/api/v1/staff/appointments")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "WALK_IN",
                                  "phone": "0988777666",
                                  "patientInfo": {
                                    "fullName": "Khách Vãng Lai Cấp Cứu",
                                    "birthYear": 1992,
                                    "gender": "MALE"
                                  },
                                  "staffId": 5,
                                  "dateTime": "%s",
                                  "temporaryService": {
                                    "durationMinutes": 30,
                                    "description": "Xử lý đau răng cấp cứu"
                                  },
                                  "isPriority": true
                                }
                                """.formatted(walkInTime)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data.source").value("WALK_IN"))
                .andExpect(jsonPath("$.data.isPriority").value(true))
                .andExpect(jsonPath("$.data.actualArrivalAt").isNotEmpty())
                .andExpect(jsonPath("$.data.services[0].isTemporary").value(true));

        // 3. L3: Lễ tân đổi lịch tự do
        OffsetDateTime newTime = ZonedDateTime.of(futureDay, LocalTime.of(11, 0), CLINIC_ZONE).toOffsetDateTime();
        mockMvc.perform(put("/api/v1/staff/appointments/{id}/reschedule", phoneApptId)
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dateTime": "%s",
                                  "staffId": 5,
                                  "chairId": 2
                                }
                                """.formatted(newTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctorId").value(5))
                .andExpect(jsonPath("$.data.chairId").value(2));

        // 4. L3: Lễ tân Check-in khách Đã đến
        mockMvc.perform(patch("/api/v1/staff/appointments/{id}/check-in", phoneApptId)
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data.actualArrivalAt").isNotEmpty());
    }

    @Test
    void testConfirmExpiredHoldAutoCancels_AndHoldExpirationBackgroundJob() throws Exception {
        String receptionistToken = loginStaff("letan01");
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        LocalDate futureDay = LocalDate.now(CLINIC_ZONE).plusDays(8);

        // 1. Lịch PENDING đã quá hạn hold_until -> Lễ tân bấm duyệt -> tự chuyển CANCELLED + trả 409 APPT_HOLD_EXPIRED
        Appointment expiredOnConfirm = appointmentRepository.saveAndFlush(Appointment.builder()
                .patient(patientProfileRepository.findById(1L).orElseThrow())
                .staff(staffAccountRepository.findById(4L).orElseThrow())
                .chair(dentalChairRepository.findById(1L).orElseThrow())
                .dateTime(ZonedDateTime.of(futureDay, LocalTime.of(9, 0), CLINIC_ZONE).toOffsetDateTime())
                .duration((short) 35)
                .status("PENDING")
                .source("ONLINE")
                .bookedByPhone("0900000001")
                .holdUntil(now.minusMinutes(10))
                .isPriority(false)
                .createdAt(now.minusHours(5))
                .build());

        mockMvc.perform(patch("/api/v1/staff/appointments/{id}/confirm", expiredOnConfirm.getAppointmentId())
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPT_HOLD_EXPIRED"));

        Appointment reloaded1 = appointmentRepository.findById(expiredOnConfirm.getAppointmentId()).orElseThrow();
        assertThat(reloaded1.getStatus()).isEqualTo("CANCELLED");
        assertThat(reloaded1.getCancelledByType()).isEqualTo("SYSTEM");

        // 2. Background Job (@Scheduled) tự động quét & hủy các lịch PENDING quá hạn hold_until
        Appointment expiredForJob = appointmentRepository.saveAndFlush(Appointment.builder()
                .patient(patientProfileRepository.findById(3L).orElseThrow())
                .staff(staffAccountRepository.findById(5L).orElseThrow())
                .chair(dentalChairRepository.findById(2L).orElseThrow())
                .dateTime(ZonedDateTime.of(futureDay, LocalTime.of(10, 0), CLINIC_ZONE).toOffsetDateTime())
                .duration((short) 35)
                .status("PENDING")
                .source("ONLINE")
                .bookedByPhone("0900000002")
                .holdUntil(now.minusMinutes(5))
                .isPriority(false)
                .createdAt(now.minusHours(4))
                .build());

        int cancelledByJob = holdExpirationJob.cancelExpiredPendingAppointments();
        assertThat(cancelledByJob).isGreaterThanOrEqualTo(1);

        Appointment reloaded2 = appointmentRepository.findById(expiredForJob.getAppointmentId()).orElseThrow();
        assertThat(reloaded2.getStatus()).isEqualTo("CANCELLED");
        assertThat(reloaded2.getCancelledByType()).isEqualTo("SYSTEM");
        assertThat(noShowEventRepository.findByAppointmentAppointmentId(expiredForJob.getAppointmentId())).isEmpty();

        Integer sysAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE actor_type = 'SYSTEM' AND action_type = 'APPOINTMENT_AUTO_CANCEL' AND target_id = ?",
                Integer.class,
                String.valueOf(expiredForJob.getAppointmentId())
        );
        assertThat(sysAuditCount).isEqualTo(1);
    }

    @Test
    void testNoShow_ReverseNoShowSameDay_AndManagerBlacklistUnlock() throws Exception {
        String receptionistToken = loginStaff("letan01");
        String managerToken = loginStaff("quanly01");
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);

        // 1. Tạo lịch CONFIRMED trong CÙNG NGÀY HÔM NAY (đã trễ quá 15 phút) để test NO_SHOW & hoàn tác trong ngày
        ZonedDateTime nowZoned = now.atZoneSameInstant(CLINIC_ZONE);
        OffsetDateTime todaySlot = nowZoned.toLocalTime().isAfter(LocalTime.of(0, 25))
                ? now.minusMinutes(25)
                : nowZoned.toLocalDate().atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
        Appointment todayAppt = appointmentRepository.saveAndFlush(Appointment.builder()
                .patient(patientProfileRepository.findById(8L).orElseThrow()) // customer 5
                .staff(staffAccountRepository.findById(6L).orElseThrow())
                .chair(dentalChairRepository.findById(2L).orElseThrow())
                .dateTime(todaySlot)
                .duration((short) 35)
                .status("CONFIRMED")
                .source("PHONE")
                .bookedByPhone("0900000005")
                .isPriority(false)
                .createdAt(now.minusHours(3))
                .createdBy(staffAccountRepository.findById(2L).orElseThrow())
                .build());

        // Kiểm tra quy tắc 15 phút: lịch hẹn tương lai chưa trễ 15 phút -> 422 NO_SHOW_TOO_EARLY
        Appointment futureAppt = appointmentRepository.saveAndFlush(Appointment.builder()
                .patient(patientProfileRepository.findById(8L).orElseThrow())
                .staff(staffAccountRepository.findById(6L).orElseThrow())
                .chair(dentalChairRepository.findById(1L).orElseThrow())
                .dateTime(now.plusHours(3))
                .duration((short) 35)
                .status("CONFIRMED")
                .source("PHONE")
                .bookedByPhone("0900000005")
                .isPriority(false)
                .createdAt(now.minusHours(1))
                .createdBy(staffAccountRepository.findById(2L).orElseThrow())
                .build());

        mockMvc.perform(patch("/api/v1/staff/appointments/{id}/no-show", futureAppt.getAppointmentId())
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_SHOW_TOO_EARLY"));

        // Đánh dấu NO_SHOW -> tạo no_show_event
        mockMvc.perform(patch("/api/v1/staff/appointments/{id}/no-show", todayAppt.getAppointmentId())
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_SHOW"));

        assertThat(noShowEventRepository.findByAppointmentAppointmentId(todayAppt.getAppointmentId())).isPresent();

        // Hoàn tác NO_SHOW -> CHECKED_IN trong cùng ngày
        mockMvc.perform(post("/api/v1/staff/appointments/{id}/reverse-no-show", todayAppt.getAppointmentId())
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reversedReason": "Khách báo kẹt xe, đến trễ 20 phút và bác sĩ đồng ý khám"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data.actualArrivalAt").isNotEmpty());

        NoShowEvent reversedEvent = noShowEventRepository.findByAppointmentAppointmentId(todayAppt.getAppointmentId()).orElseThrow();
        assertThat(reversedEvent.getIsReversed()).isTrue();

        // 2. Error Case: Hoàn tác lịch NO_SHOW ở ngày khác (lịch mẫu #6 ngày 2026-09-15) -> 422 REVERSE_NO_SHOW_EXPIRED
        mockMvc.perform(post("/api/v1/staff/appointments/6/reverse-no-show")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reversedReason": "Hoàn tác muộn"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REVERSE_NO_SHOW_EXPIRED"));

        // 3. Màn Q2: Lễ tân không được truy cập /api/v1/manager/blacklist -> 403
        mockMvc.perform(get("/api/v1/manager/blacklist")
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isForbidden());

        // Quản lý xem danh sách Blacklist (có customer 3 từ dữ liệu mẫu)
        mockMvc.perform(get("/api/v1/manager/blacklist")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.customerId == 3)].stillExceedsThreshold", contains(true)));

        // Quản lý gỡ khóa Blacklist đặc cách cho customer 3 (không xóa 3 no_show_event cũ)
        mockMvc.perform(post("/api/v1/manager/blacklist/3/unlock")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Khách hàng đã liên hệ giải trình và cam kết đến đúng giờ"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isBlacklisted").value(false))
                .andExpect(jsonPath("$.data.activeNoShowCountInWindow").value(3))
                .andExpect(jsonPath("$.data.warning").isNotEmpty());

        // Xác nhận 3 bản ghi no_show_event của customer 3 vẫn giữ nguyên chưa bị xóa/sửa & đã ghi audit_log
        Integer activeNoShowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM no_show_event e JOIN patient_profile p ON p.patient_id = e.patient_id WHERE p.customer_id = 3 AND NOT e.is_reversed",
                Integer.class
        );
        assertThat(activeNoShowCount).isEqualTo(3);

        Integer unlockAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action_type = 'BLACKLIST_UNLOCK' AND target_entity = 'customer' AND target_id = '3'",
                Integer.class
        );
        assertThat(unlockAuditCount).isEqualTo(1);
    }

    @Test
    void testDoctorSchedule_LeaveApproval_AndLeaveConflictView() throws Exception {
        String doctorToken = loginStaff("bacsi01");       // staff_id = 4
        String managerToken = loginStaff("quanly01");     // staff_id = 1
        String receptionistToken = loginStaff("letan01"); // staff_id = 2

        LocalDate targetDay = LocalDate.now(CLINIC_ZONE).plusDays(9);

        // 1. Quản lý tạo ca làm việc cho bacsi01
        mockMvc.perform(post("/api/v1/staff/schedules")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": 4,
                                  "shiftDate": "%s",
                                  "startTime": "08:00:00",
                                  "endTime": "17:00:00"
                                }
                                """.formatted(targetDay)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.staffId").value(4));

        // Bác sĩ xem lịch ca làm việc -> chỉ thấy ca của chính mình (staffId = 4)
        mockMvc.perform(get("/api/v1/staff/schedules")
                        .header("Authorization", "Bearer " + doctorToken)
                        .param("fromDate", targetDay.toString())
                        .param("toDate", targetDay.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].staffId").value(4));

        // 2. Tạo 1 lịch hẹn CONFIRMED của bacsi01 lúc 09:00 ngày targetDay
        Appointment confirmedAppt = appointmentRepository.saveAndFlush(Appointment.builder()
                .patient(patientProfileRepository.findById(1L).orElseThrow())
                .staff(staffAccountRepository.findById(4L).orElseThrow())
                .chair(dentalChairRepository.findById(1L).orElseThrow())
                .dateTime(ZonedDateTime.of(targetDay, LocalTime.of(9, 0), CLINIC_ZONE).toOffsetDateTime())
                .duration((short) 35)
                .status("CONFIRMED")
                .source("ONLINE")
                .bookedByPhone("0900000001")
                .isPriority(false)
                .createdAt(OffsetDateTime.now(CLINIC_ZONE))
                .build());

        // 3. Màn B1: Bác sĩ gửi đơn xin nghỉ đột xuất chồng lên 09:00
        MvcResult leaveRes = mockMvc.perform(post("/api/v1/doctor/leaves")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leaveDate": "%s",
                                  "startTime": "08:30:00",
                                  "endTime": "11:30:00",
                                  "reason": "Việc gia đình đột xuất"
                                }
                                """.formatted(targetDay)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        long leaveId = objectMapper.readTree(leaveRes.getResponse().getContentAsString())
                .path("data").path("leaveId").asLong();

        // 4. Màn Q1: Quản lý duyệt APPROVED -> truy vấn View v_appointment_leave_conflict trả cảnh báo lịch bị ảnh hưởng
        mockMvc.perform(patch("/api/v1/manager/leaves/{id}/approval", leaveId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leave.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.conflictCount").value(1))
                .andExpect(jsonPath("$.data.affectedAppointments[0].appointmentId").value(confirmedAppt.getAppointmentId()));

        // 5. Lễ tân lọc lịch có cảnh báo trùng nghỉ & truy vấn trực tiếp View conflicts
        mockMvc.perform(get("/api/v1/staff/appointments")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .param("date", targetDay.toString())
                        .param("onlyLeaveConflict", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.appointmentId == %d)].hasLeaveConflict".formatted(confirmedAppt.getAppointmentId()), contains(true)));

        mockMvc.perform(get("/api/v1/staff/appointments/leave-conflicts")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .param("leaveId", String.valueOf(leaveId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // 6. Lễ tân dời lịch sang 14:00 (ngoài giờ nghỉ) -> tự động hết cảnh báo trong View!
        OffsetDateTime safeAfternoon = ZonedDateTime.of(targetDay, LocalTime.of(14, 0), CLINIC_ZONE).toOffsetDateTime();
        mockMvc.perform(put("/api/v1/staff/appointments/{id}/reschedule", confirmedAppt.getAppointmentId())
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateTime": "%s"}
                                """.formatted(safeAfternoon)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasLeaveConflict").value(false));

        mockMvc.perform(get("/api/v1/staff/appointments/leave-conflicts")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .param("leaveId", String.valueOf(leaveId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void testDoctorDataIsolation_EmergencyAutoSlot_Chairs_AndLogout() throws Exception {
        String doctorToken = loginStaff("bacsi01");       // staff_id = 4
        String receptionistToken = loginStaff("letan01"); // staff_id = 2

        // 1. Bác sĩ xem /api/v1/staff/appointments -> CHỈ trả về lịch thuộc chính mình (doctorId == 4)
        MvcResult docAppts = mockMvc.perform(get("/api/v1/staff/appointments")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        var items = objectMapper.readTree(docAppts.getResponse().getContentAsString()).path("data");
        assertThat(items.isArray()).isTrue();
        for (var node : items) {
            assertThat(node.path("doctorId").asLong()).isEqualTo(4L);
        }

        // Bác sĩ cố lọc lịch của bác sĩ khác (staffId = 5) -> 403 ACCESS_DENIED_SCOPE
        mockMvc.perform(get("/api/v1/staff/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .param("staffId", "5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED_SCOPE"));

        // Bác sĩ cố tạo lịch hộ -> 403 Forbidden
        mockMvc.perform(post("/api/v1/staff/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "PHONE",
                                  "phone": "0900000001",
                                  "patientId": 1,
                                  "serviceIds": [1],
                                  "dateTime": "%s"
                                }
                                """.formatted(OffsetDateTime.now(CLINIC_ZONE).plusDays(2))))
                .andExpect(status().isForbidden());

        // 2. Lễ tân không được duyệt đơn nghỉ bác sĩ (chỉ MANAGER)
        mockMvc.perform(patch("/api/v1/manager/leaves/1/approval")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVED"}
                                """))
                .andExpect(status().isForbidden());

        // 3. Ca cấp cứu không truyền dateTime -> tự tìm khung giờ trống gần nhất
        mockMvc.perform(post("/api/v1/staff/appointments")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "PHONE",
                                  "phone": "0900000001",
                                  "patientId": 1,
                                  "serviceIds": [1],
                                  "isPriority": true,
                                  "note": "Cấp cứu tự động chèn slot gần nhất"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.isPriority").value(true))
                .andExpect(jsonPath("$.data.dateTime").isNotEmpty());

        // 4. Danh mục ghế nha đang hoạt động
        mockMvc.perform(get("/api/v1/staff/chairs")
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

        // 5. Đăng xuất nhân sự -> 200 OK & ghi audit_log
        mockMvc.perform(post("/api/v1/auth/staff/logout")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }
}
