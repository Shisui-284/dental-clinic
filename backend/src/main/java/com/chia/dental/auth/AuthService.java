package com.chia.dental.auth;

import com.chia.dental.auth.AuthDtos.*;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.*;
import com.chia.dental.repository.*;
import com.chia.dental.security.JwtService;
import com.chia.dental.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CLINIC_HOTLINE = "1900-6868";

    private final CustomerRepository customerRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsentRepository consentRepository;
    private final OtpRequestRepository otpRequestRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    @Value("${app.otp.expire-minutes:5}")
    private int otpExpireMinutes;

    @Value("${app.otp.max-retries:5}")
    private int otpMaxRetries;

    @Value("${app.otp.lock-minutes:30}")
    private int otpLockMinutes;

    @Value("${app.otp.prototype-expose-code:true}")
    private boolean exposeOtpCode;

    @Transactional(readOnly = true)
    public CheckPhoneResponse checkPhone(String phone) {
        var existingOpt = customerRepository.findByPhone(phone);
        if (existingOpt.isEmpty()) {
            return CheckPhoneResponse.builder()
                    .phone(phone)
                    .existingCustomer(false)
                    .blacklisted(false)
                    .message("Số điện thoại hợp lệ (Khách hàng mới)")
                    .patients(List.of())
                    .build();
        }

        Customer customer = existingOpt.get();
        if (Boolean.TRUE.equals(customer.getIsBlacklisted())) {
            throw new BusinessException(
                    "CUSTOMER_BLACKLISTED",
                    "Số điện thoại đang bị hạn chế tự đặt lịch trực tuyến. Vui lòng liên hệ hotline phòng khám " + CLINIC_HOTLINE + ".",
                    HttpStatus.FORBIDDEN
            );
        }

        List<PatientSummaryDto> patients = patientProfileRepository.findByCustomerCustomerId(customer.getCustomerId())
                .stream()
                .map(p -> PatientSummaryDto.builder()
                        .patientId(p.getPatientId())
                        .fullName(p.getFullName())
                        .birthYear(p.getBirthYear())
                        .gender(p.getGender())
                        .build())
                .toList();

        Long suggestedDoctorId = null;
        String suggestedDoctorName = null;
        List<Appointment> recentAppts = appointmentRepository.findRecentAppointmentsForDoctorSuggestion(customer.getCustomerId());
        if (!recentAppts.isEmpty()) {
            StaffAccount prevDoctor = recentAppts.get(0).getStaff();
            suggestedDoctorId = prevDoctor.getStaffId();
            suggestedDoctorName = prevDoctor.getFullName();
        }

        return CheckPhoneResponse.builder()
                .phone(phone)
                .existingCustomer(true)
                .blacklisted(false)
                .message("Số điện thoại hợp lệ")
                .suggestedDoctorId(suggestedDoctorId)
                .suggestedDoctorName(suggestedDoctorName)
                .patients(patients)
                .build();
    }

    @Transactional
    public OtpRequestResponse requestOtp(OtpRequestDto req) {
        checkPhone(req.getPhone());

        String purpose = req.getPurpose() != null ? req.getPurpose() : "BOOKING";
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);

        var latestOpt = otpRequestRepository.findTopByPhoneAndPurposeOrderByCreatedAtDesc(req.getPhone(), purpose);
        if (latestOpt.isPresent()) {
            OtpRequest latest = latestOpt.get();
            if (latest.getLockedUntil() != null && latest.getLockedUntil().isAfter(now)) {
                throw new BusinessException(
                        "OTP_LOCKED",
                        "Bạn đã nhập sai OTP quá số lần quy định. Vui lòng thử lại sau.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }
        }

        String rawOtp = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpRequest entity = OtpRequest.builder()
                .phone(req.getPhone())
                .otpHash(passwordEncoder.encode(rawOtp))
                .purpose(purpose)
                .createdAt(now)
                .expiredAt(now.plusMinutes(otpExpireMinutes))
                .retryCount((short) 0)
                .isUsed(false)
                .build();
        otpRequestRepository.save(entity);

        log.info("[OTP-PROTOTYPE] Gửi OTP giả lập tới SĐT {}: {}", req.getPhone(), rawOtp);

        return OtpRequestResponse.builder()
                .phone(req.getPhone())
                .purpose(purpose)
                .expireMinutes(otpExpireMinutes)
                .prototypeOtp(exposeOtpCode ? rawOtp : null)
                .build();
    }

    @Transactional
    public AuthTokenResponse verifyOtp(OtpVerifyDto req) {
        String purpose = req.getPurpose() != null ? req.getPurpose() : "BOOKING";
        OffsetDateTime now = OffsetDateTime.now(CLINIC_ZONE);

        if (req.getConsent() != null && Boolean.FALSE.equals(req.getConsent().getAccepted())) {
            throw new BusinessException("CONSENT_REQUIRED",
                    "Quý khách cần đồng ý chính sách xử lý dữ liệu cá nhân để tiếp tục.",
                    HttpStatus.BAD_REQUEST);
        }

        OtpRequest otpReq = otpRequestRepository
                .findTopByPhoneAndPurposeOrderByCreatedAtDesc(req.getPhone(), purpose)
                .orElseThrow(() -> new BusinessException("OTP_INVALID", "Chưa có yêu cầu OTP cho số điện thoại này.", HttpStatus.BAD_REQUEST));

        if (otpReq.getLockedUntil() != null && otpReq.getLockedUntil().isAfter(now)) {
            throw new BusinessException("OTP_LOCKED", "Tài khoản tạm khóa xác thực OTP trong 30 phút.", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (Boolean.TRUE.equals(otpReq.getIsUsed())) {
            throw new BusinessException("OTP_INVALID", "Mã OTP đã được sử dụng. Vui lòng yêu cầu mã mới.", HttpStatus.BAD_REQUEST);
        }
        if (otpReq.getExpiredAt().isBefore(now)) {
            throw new BusinessException("OTP_EXPIRED", "Mã OTP đã hết hạn (quá 5 phút).", HttpStatus.BAD_REQUEST);
        }

        if (!passwordEncoder.matches(req.getOtp(), otpReq.getOtpHash())) {
            short nextRetry = (short) (otpReq.getRetryCount() + 1);
            otpReq.setRetryCount(nextRetry);
            if (nextRetry >= otpMaxRetries) {
                otpReq.setLockedUntil(now.plusMinutes(otpLockMinutes));
                otpRequestRepository.save(otpReq);
                throw new BusinessException("OTP_LOCKED", "Nhập sai quá 5 lần, tạm khóa xác thực 30 phút.", HttpStatus.TOO_MANY_REQUESTS);
            }
            otpRequestRepository.save(otpReq);
            int remaining = otpMaxRetries - nextRetry;
            throw new BusinessException("OTP_INVALID", "Mã OTP không đúng. Còn lại " + remaining + " lần thử.", HttpStatus.BAD_REQUEST);
        }

        otpReq.setIsUsed(true);
        otpRequestRepository.save(otpReq);

        boolean isNewCustomer = false;
        var existingCustomerOpt = customerRepository.findByPhone(req.getPhone());
        Customer customer;
        if (existingCustomerOpt.isPresent()) {
            customer = existingCustomerOpt.get();
            if (customer.getFirstConsentAt() == null) {
                customer.setFirstConsentAt(now);
                customer = customerRepository.save(customer);
            }
        } else {
            isNewCustomer = true;
            customer = customerRepository.save(Customer.builder()
                    .phone(req.getPhone())
                    .isBlacklisted(false)
                    .firstConsentAt(now)
                    .createdAt(now)
                    .build());
        }

        if (Boolean.TRUE.equals(customer.getIsBlacklisted())) {
            throw new BusinessException(
                    "CUSTOMER_BLACKLISTED",
                    "Số điện thoại đang bị hạn chế tự đặt lịch trực tuyến. Vui lòng liên hệ hotline " + CLINIC_HOTLINE + ".",
                    HttpStatus.FORBIDDEN
            );
        }

        // Ghi nhận nhật ký đồng ý xử lý dữ liệu cá nhân (bảng consent - Màn K5 / NFR-03)
        if (req.getConsent() != null || isNewCustomer) {
            ConsentInputDto c = req.getConsent() != null ? req.getConsent() : new ConsentInputDto();
            consentRepository.save(Consent.builder()
                    .phone(customer.getPhone())
                    .customer(customer)
                    .consentTime(now)
                    .channel(c.getChannel() != null && !c.getChannel().isBlank() ? c.getChannel() : "WEB_PORTAL")
                    .action("GIVEN")
                    .policyVersion(c.getPolicyVersion() != null && !c.getPolicyVersion().isBlank() ? c.getPolicyVersion() : "v1.0-2026")
                    .consentedBy(c.getConsentedBy() != null && !c.getConsentedBy().isBlank() ? c.getConsentedBy() : "Chính chủ")
                    .relation(c.getRelation())
                    .isGuardian(Boolean.TRUE.equals(c.getIsGuardian()))
                    .build());
        }

        String token = jwtService.generateCustomerToken(customer.getCustomerId(), customer.getPhone());
        return AuthTokenResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInSeconds(jwtExpirationMs / 1000)
                .userId(customer.getCustomerId())
                .customerId(customer.getCustomerId())
                .username(customer.getPhone())
                .phone(customer.getPhone())
                .fullName("Khách hàng " + customer.getPhone())
                .role("CUSTOMER")
                .isBlacklisted(false)
                .build();
    }

    public AuthTokenResponse staffLogin(StaffLoginRequest req) {
        StaffAccount staff = staffAccountRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BusinessException("AUTH_INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không chính xác.", HttpStatus.UNAUTHORIZED));

        if (!Boolean.TRUE.equals(staff.getStatus())) {
            throw new BusinessException("AUTH_ACCOUNT_LOCKED", "Tài khoản nhân sự đã bị khóa.", HttpStatus.FORBIDDEN);
        }

        String storedHash = staff.getPasswordHash();
        boolean matched;
        if (storedHash != null && storedHash.startsWith("$2")) {
            matched = passwordEncoder.matches(req.getPassword(), storedHash);
        } else if (storedHash != null && storedHash.contains("KHONG_DUNG_THAT")) {
            // Cho phép đăng nhập tài khoản dữ liệu mẫu ở môi trường Prototype/Dev với mật khẩu 123456
            matched = "123456".equals(req.getPassword());
        } else {
            matched = false;
        }

        if (!matched) {
            throw new BusinessException("AUTH_INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không chính xác.", HttpStatus.UNAUTHORIZED);
        }

        String roleName = staff.getRole().getRoleName();
        String token = jwtService.generateStaffToken(staff.getStaffId(), staff.getUsername(), roleName);
        return AuthTokenResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInSeconds(jwtExpirationMs / 1000)
                .userId(staff.getStaffId())
                .username(staff.getUsername())
                .fullName(staff.getFullName())
                .role(roleName)
                .build();
    }

    public UserPrincipal getCurrentUser(UserPrincipal principal) {
        return principal;
    }
}
