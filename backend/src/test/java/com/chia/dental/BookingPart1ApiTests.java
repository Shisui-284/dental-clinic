package com.chia.dental;

import com.chia.dental.booking.AppointmentBookingService;
import com.chia.dental.entity.*;
import com.chia.dental.repository.*;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingPart1ApiTests {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentBookingService bookingService;

    @Autowired
    private DoctorScheduleRepository doctorScheduleRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private DentalChairRepository dentalChairRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void ensureDoctorShiftsForDate(LocalDate date) {
        for (long doctorId : new long[]{4L, 5L, 6L}) {
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
    }

    private String loginCustomerWithOtp(String phone) throws Exception {
        MvcResult otpRes = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s", "purpose": "BOOKING"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        String otp = objectMapper.readTree(otpRes.getResponse().getContentAsString())
                .path("data").path("prototypeOtp").asText();

        MvcResult verifyRes = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "%s",
                                  "otpCode": "%s",
                                  "purpose": "BOOKING",
                                  "consent": {
                                    "accepted": true,
                                    "policyVersion": "v1.0-2026",
                                    "channel": "WEB_PORTAL",
                                    "consentedBy": "Khách test",
                                    "relation": "SELF",
                                    "isGuardian": false
                                  }
                                }
                                """.formatted(phone, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andReturn();

        return objectMapper.readTree(verifyRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    @Test
    void testCheckPhone_SuggestsPreviousDoctorAndBlocksBlacklisted() throws Exception {
        // Khách cũ 0900000001 -> gợi ý bác sĩ cũ + danh sách hồ sơ bệnh nhân
        mockMvc.perform(post("/api/v1/auth/customer/check-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "0900000001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existingCustomer").value(true))
                .andExpect(jsonPath("$.data.suggestedDoctorId").isNumber())
                .andExpect(jsonPath("$.data.patients", hasSize(greaterThanOrEqualTo(2))));

        // Khách bị Blacklist 0900000003 -> 403 CUSTOMER_BLACKLISTED
        mockMvc.perform(post("/api/v1/auth/customer/check-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "0900000003"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUSTOMER_BLACKLISTED"));
    }

    @Test
    void testDurationBufferAndAvailableSlots_NewVsExistingPatient() throws Exception {
        LocalDate targetDate = LocalDate.now(CLINIC_ZONE).plusDays(2);
        ensureDoctorShiftsForDate(targetDate);

        // Bệnh nhân cũ (patientId=1): service 1 (30p) + service 3 (45p) + chairBuffer (5p) + 0p = 80p
        mockMvc.perform(get("/api/v1/public/appointments/available-slots")
                        .param("fromDate", targetDate.toString())
                        .param("toDate", targetDate.toString())
                        .param("serviceIds", "1,3")
                        .param("patientId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceDurationMinutes").value(75))
                .andExpect(jsonPath("$.data.chairBufferMinutes").value(5))
                .andExpect(jsonPath("$.data.newPatientBufferMinutes").value(0))
                .andExpect(jsonPath("$.data.totalCalculatedDuration").value(80))
                .andExpect(jsonPath("$.data.isNewPatient").value(false))
                .andExpect(jsonPath("$.data.days", hasSize(1)))
                .andExpect(jsonPath("$.data.days[0].slots[0].availableChairId").doesNotExist());

        // Bệnh nhân mới (SĐT chưa có hồ sơ): +15p đệm hồ sơ mới -> tổng 95p
        mockMvc.perform(get("/api/v1/public/appointments/available-slots")
                        .param("fromDate", targetDate.toString())
                        .param("toDate", targetDate.toString())
                        .param("serviceIds", "1,3")
                        .param("phone", "0999888777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewPatient").value(true))
                .andExpect(jsonPath("$.data.newPatientBufferMinutes").value(15))
                .andExpect(jsonPath("$.data.totalCalculatedDuration").value(95));

        // Quá 14 ngày cho khách -> 400 INVALID_DATE_RANGE
        mockMvc.perform(get("/api/v1/public/appointments/available-slots")
                        .param("fromDate", targetDate.toString())
                        .param("toDate", targetDate.plusDays(20).toString())
                        .param("serviceIds", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void testCalculateHoldUntilFormula() {
        // Case 1: Đặt 09:00 ngày T cho lịch 10:00 ngày T+2 -> min(21:00 T, 09:00 T+2) = 21:00 T (ngoài giờ 18:00) -> đẩy sang 08:00 T+1
        OffsetDateTime created1 = OffsetDateTime.of(2026, 12, 1, 9, 0, 0, 0, ZoneOffset.ofHours(7));
        OffsetDateTime appt1 = OffsetDateTime.of(2026, 12, 3, 10, 0, 0, 0, ZoneOffset.ofHours(7));
        OffsetDateTime hold1 = bookingService.calculateHoldUntil(created1, appt1);
        assertThat(hold1).isEqualTo(OffsetDateTime.of(2026, 12, 2, 8, 0, 0, 0, ZoneOffset.ofHours(7)));

        // Case 2: Đặt 08:30 cho lịch 11:00 cùng ngày -> min(20:30, 10:00) = 10:00 (trong giờ làm) -> 10:00
        OffsetDateTime created2 = OffsetDateTime.of(2026, 12, 1, 8, 30, 0, 0, ZoneOffset.ofHours(7));
        OffsetDateTime appt2 = OffsetDateTime.of(2026, 12, 1, 11, 0, 0, 0, ZoneOffset.ofHours(7));
        OffsetDateTime hold2 = bookingService.calculateHoldUntil(created2, appt2);
        assertThat(hold2).isEqualTo(OffsetDateTime.of(2026, 12, 1, 10, 0, 0, 0, ZoneOffset.ofHours(7)));

        // Case 3: Đặt 21:00 cho lịch 08:15 sáng hôm sau -> 07:15 đẩy sang 08:00 nhưng bị chặn trên tại dateTime - 30p = 07:45
        OffsetDateTime created3 = OffsetDateTime.of(2026, 12, 1, 21, 0, 0, 0, ZoneOffset.ofHours(7));
        OffsetDateTime appt3 = OffsetDateTime.of(2026, 12, 2, 8, 15, 0, 0, ZoneOffset.ofHours(7));
        OffsetDateTime hold3 = bookingService.calculateHoldUntil(created3, appt3);
        assertThat(hold3).isEqualTo(OffsetDateTime.of(2026, 12, 2, 7, 45, 0, 0, ZoneOffset.ofHours(7)));
    }

    @Test
    void testCustomerBookingAutoAssign_Reschedule_Cancel_Ics_AndAuditLog() throws Exception {
        LocalDate day1 = LocalDate.now(CLINIC_ZONE).plusDays(2);
        LocalDate day2 = LocalDate.now(CLINIC_ZONE).plusDays(3);
        ensureDoctorShiftsForDate(day1);
        ensureDoctorShiftsForDate(day2);

        String customer1Token = loginCustomerWithOtp("0900000001");
        String customer2Token = loginCustomerWithOtp("0900000002");

        // Error Case: Đặt quá sát (< 2 giờ) -> 422 APPT_BOOKING_WINDOW_VIOLATION
        OffsetDateTime tooSoon = OffsetDateTime.now(CLINIC_ZONE).plusMinutes(30);
        mockMvc.perform(post("/api/v1/customer/appointments")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": 1,
                                  "staffId": 4,
                                  "dateTime": "%s",
                                  "serviceIds": [1]
                                }
                                """.formatted(tooSoon)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPT_BOOKING_WINDOW_VIOLATION"));

        // Happy Case: Đặt cho người thân mới, bỏ trống staffId (Auto-Assignment) -> duration = 30 + 5 + 15 = 50p
        OffsetDateTime apptTime = ZonedDateTime.of(day1, LocalTime.of(9, 0), CLINIC_ZONE).toOffsetDateTime();
        MvcResult createRes = mockMvc.perform(post("/api/v1/customer/appointments")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingFor": "RELATIVE",
                                  "patientInfo": {
                                    "fullName": "Nguyễn Bé Nhỏ",
                                    "birthYear": 2019,
                                    "gender": "MALE",
                                    "relation": "CHILD",
                                    "isGuardian": true
                                  },
                                  "dateTime": "%s",
                                  "serviceIds": [1],
                                  "note": "Bé đi khám lần đầu"
                                }
                                """.formatted(apptTime)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.duration").value(50))
                .andExpect(jsonPath("$.data.doctorAutoAssigned").value(true))
                .andExpect(jsonPath("$.data.appointmentCode", startsWith("LH-")))
                .andReturn();

        long apptId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("appointmentId").asLong();

        // Tải file .ics
        mockMvc.perform(get("/api/v1/customer/appointments/{id}/ics", apptId)
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".ics")))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")))
                .andExpect(content().string(containsString("BEGIN:VEVENT")));

        // Error Case: IDOR - Khách hàng 2 cố tình tải .ics / hủy lịch của khách hàng 1 -> 403 ACCESS_DENIED_SCOPE
        mockMvc.perform(get("/api/v1/customer/appointments/{id}/ics", apptId)
                        .header("Authorization", "Bearer " + customer2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED_SCOPE"));

        // Happy Case: Đổi lịch khi còn >= 4 giờ
        OffsetDateTime newTime = ZonedDateTime.of(day2, LocalTime.of(10, 0), CLINIC_ZONE).toOffsetDateTime();
        mockMvc.perform(put("/api/v1/customer/appointments/{id}/reschedule", apptId)
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dateTime": "%s",
                                  "staffId": 5
                                }
                                """.formatted(newTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctorId").value(5));

        // Happy Case: Hủy lịch khi còn >= 4 giờ
        mockMvc.perform(patch("/api/v1/customer/appointments/{id}/cancel", apptId)
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cancelReason": "Gia đình có việc bận"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Kiểm tra đã ghi đủ 3 bản ghi audit_log (CREATE, RESCHEDULE, CANCEL)
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE target_entity = 'appointment' AND target_id = ?",
                Integer.class,
                String.valueOf(apptId)
        );
        assertThat(auditCount).isEqualTo(3);

        // Error Case: Không cho phép tự hủy khi còn dưới 4 giờ -> 422 APPT_CHANGE_WINDOW_VIOLATION
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);
        Appointment nearAppt = appointmentRepository.saveAndFlush(Appointment.builder()
                .patient(patientProfileRepository.findById(1L).orElseThrow())
                .staff(staffAccountRepository.findById(6L).orElseThrow())
                .chair(dentalChairRepository.findById(1L).orElseThrow())
                .dateTime(now.plusHours(2))
                .duration((short) 30)
                .status("CONFIRMED")
                .source("ONLINE")
                .bookedByPhone("0900000001")
                .isPriority(false)
                .createdAt(now.minusHours(5))
                .build());

        mockMvc.perform(patch("/api/v1/customer/appointments/{id}/cancel", nearAppt.getAppointmentId())
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cancelReason": "Muốn hủy gấp"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPT_CHANGE_WINDOW_VIOLATION"));
    }
}
