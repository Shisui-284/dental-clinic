# CHIA Dental Clinic — Backend Prototype (WBS 6.3)

## 1. Công nghệ sử dụng
- **Java 21** (tương thích JDK 24) & **Spring Boot 3.4.5**
- **Spring Web MVC** + **Spring Validation**
- **Spring Data JPA (Hibernate 6)** + **PostgreSQL 18** (`dental_clinic`)
- **Spring Security 6** + **JWT (JJWT 0.12.6)**
- **Springdoc OpenAPI 3 (Swagger UI)**
- **Lombok**

## 2. Cấu trúc Source Code
```
backend/src/main/java/com/chia/dental/
├── DentalBackendApplication.java
├── auth/
│   ├── AuthController.java         # /api/v1/auth/** (OTP giả lập, Staff Login, /me)
│   ├── AuthDtos.java
│   └── AuthService.java
├── booking/
│   ├── AppointmentBookingService.java
│   ├── AppointmentDtos.java
│   ├── CustomerAppointmentController.java # /api/v1/customer/appointments
│   ├── PublicCatalogService.java
│   ├── PublicController.java              # /api/v1/public/** (services, doctors, slots)
│   ├── PublicDtos.java
│   └── StaffAppointmentController.java    # /api/v1/staff/appointments (list, confirm, cancel)
├── common/
│   ├── exception/
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
│   └── response/
│       ├── ApiResponse.java
│       └── FieldErrorDetail.java
├── config/
│   ├── OpenApiConfig.java
│   └── SecurityConfig.java
├── entity/                         # JPA Entities ánh xạ bảng PostgreSQL 6.1
├── repository/                     # Spring Data JPA Repositories
└── security/
    ├── JwtAuthenticationFilter.java
    ├── JwtService.java
    └── UserPrincipal.java
```

## 3. Tài khoản dữ liệu mẫu (Test Accounts)
- **Nhân sự (Staff Login)**:
  - Lễ tân: `letan01` / `123456` (Role: `RECEPTIONIST`)
  - Quản lý: `quanly01` / `123456` (Role: `MANAGER`)
  - Bác sĩ: `bacsi01` / `123456` (Role: `DOCTOR`)
- **Khách hàng (OTP giả lập)**:
  - SĐT mẫu: `0900000001` (hoặc bất kỳ SĐT hợp lệ chưa bị Blacklist)
  - Gọi `POST /api/v1/auth/otp/request` sẽ trả về trường `prototypeOtp` ngay trong JSON để test tiếp `POST /api/v1/auth/otp/verify`.

## 4. Hướng dẫn chạy & Swagger UI
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

