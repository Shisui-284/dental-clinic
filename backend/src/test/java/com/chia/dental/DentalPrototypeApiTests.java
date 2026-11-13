package com.chia.dental;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DentalPrototypeApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testPublicCatalogAndAvailableSlots_HappyAndHolidayCases() throws Exception {
        // 1. Lấy danh sách dịch vụ
        mockMvc.perform(get("/api/v1/public/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(8))));

        // 2. Lấy danh sách bác sĩ
        mockMvc.perform(get("/api/v1/public/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)));

        // 3. Lấy slot trống ngày 2026-11-20 (có ca làm việc trong sample data)
        mockMvc.perform(get("/api/v1/public/appointments/available-slots")
                        .param("date", "2026-11-20")
                        .param("serviceIds", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holiday").value(false))
                .andExpect(jsonPath("$.data.slots", hasSize(greaterThan(0))));

        // 4. Error/Edge Case: Ngày nghỉ lễ 2027-01-01 -> holiday = true, slots rỗng
        mockMvc.perform(get("/api/v1/public/appointments/available-slots")
                        .param("date", "2027-01-01")
                        .param("serviceIds", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holiday").value(true))
                .andExpect(jsonPath("$.data.slots", hasSize(0)));
    }

    @Test
    void testEndToEndBookingAndReceptionistConfirmFlow() throws Exception {
        // 1. Khách hàng yêu cầu OTP
        MvcResult otpRes = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "0900000001", "purpose": "BOOKING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prototypeOtp").isNotEmpty())
                .andReturn();

        String otp = objectMapper.readTree(otpRes.getResponse().getContentAsString())
                .path("data").path("prototypeOtp").asText();

        // 2. Error Case: Nhập sai OTP
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "0900000001", "otp": "000000", "purpose": "BOOKING"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));

        // 3. Happy Case: Verify OTP đúng -> nhận Customer JWT
        MvcResult verifyRes = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "0900000001", "otp": "%s", "purpose": "BOOKING"}
                                """.formatted(otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andReturn();

        String customerToken = objectMapper.readTree(verifyRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 4. Happy Case: Khách hàng đặt lịch trực tuyến (tạo bản ghi PENDING)
        MvcResult createApptRes = mockMvc.perform(post("/api/v1/customer/appointments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": 1,
                                  "doctorId": 4,
                                  "dateTime": "2026-11-20T09:00:00+07:00",
                                  "serviceIds": [1],
                                  "note": "Test prototype booking"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        long appointmentId = objectMapper.readTree(createApptRes.getResponse().getContentAsString())
                .path("data").path("appointmentId").asLong();

        // 5. Error Case: Lễ tân đăng nhập sai mật khẩu -> 401
        mockMvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "letan01", "password": "wrongpassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        // 6. Happy Case: Lễ tân đăng nhập đúng -> nhận Staff JWT
        MvcResult staffLoginRes = mockMvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "letan01", "password": "123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("RECEPTIONIST"))
                .andReturn();

        String staffToken = objectMapper.readTree(staffLoginRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 7. Happy Case: Lễ tân duyệt lịch PENDING -> CONFIRMED
        mockMvc.perform(patch("/api/v1/staff/appointments/{id}/confirm", appointmentId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // 8. Error Case: Duyệt lại lịch đã CONFIRMED -> 400 INVALID_STATUS_TRANSITION
        mockMvc.perform(patch("/api/v1/staff/appointments/{id}/confirm", appointmentId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }
}

