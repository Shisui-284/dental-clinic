# Phân hệ CSKH tự động hóa — backend

Bám SRS: mục 2.2 (CSKH), 3.2 (UC-CSKH-01..10), 4.1 (dữ liệu), 4.2.7/4.3 (quy tắc, NFR), phần "6 điểm mâu thuẫn đã chốt".

## 1. Cách chạy
1. Chạy `db/01_schema.sql`, `db/02_sample_data.sql`, rồi **`db/05_cskh_patch.sql`** (bắt buộc: `ddl-auto: validate` sẽ lỗi nếu thiếu cột `care_task.visible_from`...).
2. **Không cần sửa `application.yml`**: mọi giá trị đều có mặc định trong code. Chu kỳ job nằm chung nhóm `app.scheduling.*` với `HoldExpirationJob`; tắt tất cả bằng `app.scheduling.enabled=false`. Muốn bật Zalo thật hoặc đổi chu kỳ thì dùng mẫu `docs/application-cskh.example.yml`.
3. Biến môi trường (tùy chọn): `ZALO_ENABLED=true`, `ZALO_ACCESS_TOKEN`, `ZALO_TPL_REMINDER`, `ZALO_TPL_POST_VISIT`, `ZALO_TPL_RECALL`, `CARE_FEEDBACK_BASE_URL`, `CARE_SCHEDULER_ENABLED`.
4. Không cấu hình Zalo thì mọi tin tự chuyển sang SMS (đang mock, chỉ ghi log).

## 2. Tự động hóa chạy thế nào
| Tiến trình | Chu kỳ | Việc làm |
|---|---|---|
| `dispatch` | 60s | Quét lịch Đã xác nhận, tạo việc nhắc lịch trước 24h và 2h; gửi mọi tin đến hạn |
| `flagOverdue` | 5 phút | Đánh dấu quá hạn SLA cho việc Chờ chăm sóc / Cần hỗ trợ chuyên môn |
| `reconcile` | 10 phút | Bù việc CSKH cho lịch Đã hoàn tất 3 ngày gần nhất mà sự kiện bị lỡ |

Luồng: bác sĩ `PATCH /staff/appointments/{id}/complete` → sự kiện sau commit → `CareTriggerService` tạo việc **Hỏi thăm sau khám** (kèm tin tự động + link đánh giá) và **Nhắc tái khám** (nếu có ngày bác sĩ đề xuất hoặc dịch vụ có chu kỳ) → giao lễ tân vòng tròn → lễ tân xử lý.

Gửi tin: kiểm tra còn phù hợp (lịch còn Đã xác nhận, đúng giờ hẹn, việc chưa đóng) → kiểm tra opt-out → Zalo ZNS → nếu bị từ chối thì SMS → mỗi lần gửi/chuyển kênh ghi 1 dòng `notification_log`. Chỉ ghi Thành công khi nhà cung cấp trả mã xác nhận. Timeout/5xx/429 được thử lại (tối đa `notification_max_attempts`, mặc định 3) thay vì chuyển kênh ngay, để tránh gửi trùng.

## 3. API (theo quy ước URL của team: `/staff`, `/doctor`, `/manager`; Swagger tag 7–13)
**Lễ tân/Quản lý** — `/api/v1/staff/care`:
`GET tasks`, `GET tasks/{id}`, `PATCH tasks/{id}/start | request-medical-support | close`, `POST tasks/{id}/send-recall`, `GET upcoming-appointments`, `POST upcoming-appointments/{appointmentId}/confirm-call`, `GET customers`, `GET customers/{id}`, `PUT customers/{id}/opt-out`, `GET notification-logs`, `GET dashboard`, `GET/POST complaints`, `GET complaints/{id}`, `PATCH complaints/{id}/status`, `GET feedback`.
**Bác sĩ** — `/api/v1/doctor/care`: `GET medical-support-tasks`, `PATCH tasks/{id}/medical-response`. Bác sĩ cũng gọi được `GET /staff/care/feedback` và `/complaints` nhưng chỉ thấy ca mình khám (SĐT bị ẩn).
**Quản lý** — `/api/v1/manager/care`: `GET reports/summary`, `PATCH feedback/{id}/hide|unhide`, `PATCH complaints/{id}/hide|unhide` (chỉ vai trò MANAGER dù URL `/manager/**` mở cho lễ tân).
**Công khai (K9)** — `GET|POST /api/v1/public/feedback/{token}`.
**Hoàn tất khám tối thiểu (UC-14)** — `PATCH /api/v1/doctor/appointments/{id}/complete` (bác sĩ phụ trách, lịch phải Đã đến).

## 4. Đối chiếu SRS → code
| SRS | Hiện thực |
|---|---|
| FR-21/UC-CSKH-01 tra cứu 360 | `CareQueryRepository.customerOverview` — không trả hồ sơ y tế/tiền sử bệnh (thuộc phân hệ hồ sơ) |
| FR-22/27 nhắc 24h & 2h, Zalo→SMS | `NotificationJobService.scanReminders/process`, `ZaloZnsSender`, `MockSmsSender` |
| FR-23 gọi xác nhận lịch 24–48h | `upcoming-appointments`, `confirm-call` (tạo `CONFIRM_CALL` đã đóng) |
| FR-24/25 hỏi thăm sau khám theo kịch bản | `CareTriggerService` + `care_scenario` (ưu tiên MINOR_SURGERY > PROSTHETIC > LONG_TERM > GENERAL) |
| Nhắc tái khám, không tự tạo lịch | việc `RECALL` + `send-recall` chỉ gửi tin mời |
| FR-26/37 SLA 48h, quá hạn là badge | `WorkingTimeCalculator`, `flagOverdue`; đóng bắt buộc `result_note` |
| 4 trạng thái Care_Task | migration 05 (`NEEDS_MEDICAL`) + `CareTaskService` |
| FR-28 nhật ký gửi tin | `notification_log` (append-only) + `notification_job` (vòng đời, lý do bỏ qua) |
| FR-29 opt-out | `OptOutPolicy`: chặn nhắc lịch/hỏi thăm/tái khám/sinh nhật; không bao giờ chặn OTP và xác nhận/đổi/hủy lịch |
| FR-30..33 đánh giá, cảnh báo đỏ, khiếu nại | `FeedbackService`, `SymptomDetector`, `ComplaintService`; token dùng một lần (SHA-256), ≤3 sao hoặc có từ khóa → khiếu nại + hạn 48h; ẩn chỉ Quản lý, bắt buộc lý do, không xóa |
| NFR-04 audit | dùng `AuditLogService` của team, ghi cùng transaction với thao tác |
| NFR-12 đổi nhà cung cấp | interface `NotificationSender` |

## 4b. Tích hợp với code của team (bản gộp)
- **Không sửa file nào của đồng đội** (kể cả `application.yml`). Toàn bộ phần CSKH là file mới: package `care/`, `treatment/`, 5 entity + 5 repository mới, `db/05_cskh_patch.sql`.
- **Dùng lại**: `AuditLogService` (audit), `ClinicConfigService` (đọc số nguyên/giờ qua `CareSettings`), `ClinicHolidayRepository` (ngày nghỉ), `SchedulingConfig` (bật `@EnableScheduling`), `ApiResponse`/`BusinessException`.
- **Bỏ bản trùng của tôi**: `AuditService`, `ClinicConfigService` (trùng tên bean với bản của team sẽ làm app không khởi động), `@EnableScheduling` riêng.
- **`CareSettings`** là lớp mỏng bổ sung kiểu chuỗi/danh sách (giờ nhắc, từ khóa cảnh báo, ngày làm việc) mà `ClinicConfigService` chưa có.
- Nhắc lịch không cần hook vào `AppointmentBookingService`: khi lịch bị hủy hoặc đổi giờ, việc gửi tự bị bỏ qua lúc xử lý (kiểm tra trạng thái và giờ hẹn).

## 5. Quyết định đã chốt
- Zalo ZNS thật, SMS mock. · SLA 48h chỉ tính giờ làm việc. · Giao việc vòng tròn giữa lễ tân đang hoạt động (không có lễ tân → Quản lý).

## 6. Giả định tôi tự đặt (cần bạn xác nhận) và chỗ lệch SRS
1. **Mốc bắt đầu SLA = `visible_from`** (lúc việc đến hạn xuất hiện), không phải `created_at` như SRS 4.3.3 ghi. Lý do: việc nhắc tái khám tạo sẵn trước nhiều tháng, hỏi thăm sau khám đến hạn sau 1–3 ngày; tính từ `created_at` thì việc quá hạn ngay khi chưa đến hạn. Dữ liệu mẫu cũng tính SLA từ `scheduled_at`.
2. **Ngày làm việc = T2–T7** (`clinic_working_days`), giờ mở/đóng lấy `clinic_open_time/close_time` (mặc định 08:00–18:00). SRS không nêu ngày nghỉ hằng tuần.
3. Hỏi thăm sau khám lấy **`care_scenario.followup_min_days`** (dữ liệu mẫu đều = 1 ngày), tin được dời vào giờ làm việc. Có thể chỉnh trong DB; tôi không hard-code số ngày.
4. Cửa sổ nhắc lịch: chỉ tạo nhắc nếu đang trong 30 phút kể từ mốc (`reminder_grace_minutes`) để lịch đặt sát giờ không bị nhắc dồn.
5. Từ khóa cảnh báo mặc định chỉ có 2 cụm SRS nêu + vài biến thể (`alert_keywords`); mức độ khiếu nại: có từ khóa → HIGH, ≤2 sao → MEDIUM, 3 sao → LOW.
6. Lễ tân đóng việc phải qua Đang xử lý (đúng chuỗi trạng thái SRS), không đóng thẳng từ Chờ chăm sóc.
7. Link đánh giá hạn 14 ngày (`feedback_link_ttl_days`), token 128-bit, cấp lại khi gửi lại tin.
8. Migration gán kịch bản PROSTHETIC cho "Tẩy trắng răng" (SRS 2.2.2 xếp vào nhóm phục hình/thẩm mỹ) và thêm lưu ý "ăn no trước khi nhổ răng khôn" (ví dụ của SRS) vào cột mới `service.pre_visit_instruction`.

## 7. Chưa làm / hạn chế (nói thẳng)
- **Chưa biên dịch và chưa chạy thử** (môi trường làm việc không có JDK/Maven/Postgres/mạng). Cần `./mvnw test` và chạy thử với DB thật; có thể còn lỗi nhỏ.
- Zalo: chưa tự refresh access token; mã lỗi "SĐT không dùng Zalo" chưa đối chiếu tài liệu Zalo (mọi lỗi rõ ràng đều chuyển SMS); tên tham số template (`MessageComposer`) phải khớp template đã duyệt.
- SMS Brandname thật: chưa có (mock). Giới hạn độ dài SMS chưa xử lý.
- Tin xác nhận/đổi/hủy lịch (FR-27 phần giao dịch) thuộc luồng đặt lịch hiện có: hàng đợi đã hỗ trợ khái niệm nhưng chưa nối vào `AppointmentBookingService`.
- Không tự đóng việc nhắc tái khám khi khách đã đặt lịch mới; không có chúc mừng sinh nhật tự động; chưa phân loại uy tín khách (SRS không nêu ngưỡng).
- Nghỉ phép/ca trực của lễ tân chưa có dữ liệu nên chia việc chỉ theo tài khoản đang hoạt động.
- `complete` mới là bản tối thiểu của UC-14.
- Test mới chỉ là unit test (`SymptomDetectorTest`, `WorkingTimeCalculatorTest` có dùng Mockito); chưa có test tích hợp cho SQL. Các test `@SpringBootTest` có sẵn của team cần DB đã chạy `05_cskh_patch.sql`.
- `complete` có thể trùng với UC-14 đầy đủ nếu đồng đội làm sau: khi đó giữ một nơi duy nhất, chỉ cần phát `AppointmentCompletedEvent` (hoặc gọi `CareTriggerService.onAppointmentCompleted`).
