# 7.2 — Frontend phân hệ đặt lịch (desktop 1440×900)

Bản triển khai frontend cho card **7.2**, dựa trên:
- `5.2_ThietKeGiaoDien.md` — đặc tả K1–K7, L1–L3, microcopy, trạng thái.
- `5.3 (1).docx` — API Contract (endpoint `/api/v1/...`).
- `prototype/` (card 6.2) — quy ước desktop 1440×900, không mobile, nhãn trạng thái tiếng Việt.
- `dc_db/` — trạng thái lịch 6 giá trị.

## Phạm vi

| Màn | Nội dung | Trạng thái |
|---|---|---|
| K1 | Nhập SĐT, kiểm tra blacklist | ✅ |
| K2 | Chọn người khám (bản thân/người thân) | ✅ |
| K3 | Chọn dịch vụ + bác sĩ, tóm tắt thời lượng/giá | ✅ |
| K4 | Chọn ngày/khung giờ trống động (14 ngày), chế độ đổi lịch | ✅ |
| K5 | OTP 6 số + consent → tạo lịch, xử lý 409 | ✅ |
| K6 | Kết quả Chờ xác nhận + đếm ngược Hold_Until + tải .ics | ✅ |
| K7 | Lịch của tôi, đổi/hủy (chặn < 4 giờ), drawer chi tiết | ✅ |
| L1 | Duyệt lịch PENDING, chốt bác sĩ/ghế, từ chối có lý do | ✅ |
| L2 | Tạo lịch hộ / vãng lai / cấp cứu / tái khám | ✅ |
| L3 | Điều phối trong ngày, Đã đến / Không đến / Hoàn tác | ✅ |
| N1 | Đăng nhập nhân sự (rút gọn, cần cho L1–L3) | ✅ |

Ngoài phạm vi 7.2: L4–L6, B1–B2, Q1–Q5 (hiển thị mờ trong sidebar, thuộc card 8.2–9.x).

## Chạy

Cần static server (ES modules + `fetch` không chạy được qua `file://`):

```powershell
cd E:\QLDAPM\frontend
python -m http.server 5173
# hoặc: npm start   (npx serve)
```

Mở `http://localhost:5173/`.

- Cổng khách: `#/k1`
- Cổng nhân sự: `#/l1` → tự chuyển sang đăng nhập
- Tài khoản demo (mock): `letan01` / `demo1234`, `quanly01` / `demo1234`

## Hai nguồn dữ liệu

Nút **Mock API / Live API** ở góc dưới phải:

| Chế độ | Hành vi |
|---|---|
| `mock` (mặc định) | Dùng `assets/js/core/mock.js` — dữ liệu mẫu trong `localStorage`, không cần backend |
| `live` | Gọi `API_BASE + /api/v1/...` thật, gửi `Authorization: Bearer <JWT>` |

Cấu hình lưu ở `localStorage` (`qldapm_frontend_72`). Khi backend 7.1 chạy, chỉ cần đổi `apiBase` (mặc định `http://localhost:8080`) và bật CORS cho origin frontend. **Không cần sửa màn hình** vì cả hai chế độ dùng chung một lớp `api/`.

### Nối nhanh backend 7.1 bằng query string

```
http://localhost:5173/?mode=live&api=http://localhost:8080#/k1
```

Query string chỉ ghi đè cho phiên hiện tại, không lưu `localStorage`; bỏ query là quay lại cấu hình đã lưu.

### Chạy backend 7.1 trên máy này

Repo backend: `https://github.com/Shisui-284/dental-clinic` (nhánh `main`, thư mục `backend/`).
Bản clone dùng để nối thử: `E:\dental-clinic`.

```powershell
# Cách nhanh: script khởi động PostgreSQL dev (cổng 5433) + backend (cổng 8080)
powershell -ExecutionPolicy Bypass -File E:\QLDAPM\tools\start_backend_live.ps1

# Hoặc thủ công
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5433/dental_clinic?currentSchema=public"
$env:DB_PASSWORD="123456"
cd E:\dental-clinic\backend
java -jar target\dental-backend-0.1.0-SNAPSHOT.jar
```

- PostgreSQL dev nằm ở `E:\dental-clinic\.pgdata`, **cổng 5433**, chỉ nghe `127.0.0.1`, auth `trust` — tách biệt hoàn toàn với PostgreSQL 17 mặc định ở cổng 5432 (không cần biết mật khẩu `postgres` sẵn có, không đụng dữ liệu cũ).
- Database `dental_clinic` đã nạp `db/01_schema.sql` + `db/02_sample_data.sql`.
- Tài khoản backend mẫu: `letan01` / `123456`, `quanly01` / `123456`, `bacsi01` / `123456`.
- Swagger UI: `http://localhost:8080/swagger-ui.html`.

### Nếu muốn dùng PostgreSQL 17 sẵn có (cổng 5432)

Mật khẩu `postgres` **không xem lại được** (PostgreSQL chỉ lưu hash), chỉ có thể đặt lại:

1. Mở **pgAdmin** đã cài; nếu còn phiên đăng nhập thì vào Query Tool và chạy:
   ```sql
   ALTER USER postgres PASSWORD 'mat-khau-moi';
   ```
2. Nếu không đăng nhập được: sửa `C:\Program Files\PostgreSQL\17\data\pg_hba.conf`, đổi `scram-sha-256` thành `trust` cho các dòng `host ... 127.0.0.1/32`, restart service `postgresql-x64-17`, chạy `ALTER USER`, rồi **khôi phục `scram-sha-256`** và restart lại. Cần quyền Administrator.

Sau đó chạy backend với `DB_PASSWORD` mới và bỏ `SPRING_DATASOURCE_URL` (mặc định `localhost:5432`).

### LiveAdapter — điểm chuyển đổi duy nhất

`assets/js/api/live.js` chuyển shape backend 7.1 ↔ shape nội bộ của màn hình:

| Khác biệt | Backend 7.1 | Frontend 7.2 |
|---|---|---|
| Envelope | `{ success, code, message, data }` | `http.js` bóc `data` tự động |
| ID | số (`appointmentId`, `staffId`) | chuỗi (`"17"`, `"4"`) |
| Lịch hẹn | `dateTime`, `duration`, `bookedByPhone` | `start`, `duration`, `phone` |
| Danh sách | mảng trần | `{ items }` |
| Khung giờ | `days[].slots[].startTime` (`"08:30"`) | ISO `2026-11-20T08:30:00+07:00` |
| Khách | `tab=UPCOMING\|PAST`, lọc theo JWT | `scope=upcoming\|past`, tham số `phone` bỏ qua |
| Nguồn | `ONLINE / PHONE / SOCIAL / WALK_IN` | `CUSTOMER / RECEPTION / SOCIAL / WALK_IN` |
| Bác sĩ nghỉ | `/staff/leaves?status=APPROVED` | `{ leaves: [...] }` cho banner L3 |
| Ghế | `chairId` số, tự gán | Mock `GH1..GH4`; live ẩn chọn ghế ở L1 |

Token được chọn theo cổng: `/api/v1/customer/**` dùng Customer JWT, `/api/v1/staff/**` dùng Staff JWT (tránh dùng nhầm token khi vừa đăng nhập nhân sự vừa mở cổng khách).

## Cấu trúc

```
frontend/
├── index.html
├── assets/css/app.css          # design token 5.2 (teal #0F766E), desktop 1440
├── assets/js/
│   ├── app.js                  # bootstrap + router + guard RBAC + cấu hình API
│   ├── config.js
│   ├── api/index.js            # chọn Mock/Live, ánh xạ endpoint 5.3
│   ├── api/live.js             # LiveAdapter: chuyển shape backend 7.1 ↔ UI
│   ├── core/
│   │   ├── http.js             # fetch + envelope + token theo cổng + timeout
│   │   ├── mock.js             # MockAdapter (dữ liệu mẫu, cùng shape API)
│   │   ├── session.js          # JWT khách/nhân sự, draft đặt lịch
│   │   ├── router.js           # hash router
│   │   ├── shell.js            # khung cổng khách / cổng nhân sự
│   │   ├── format.js           # ngày giờ, tiền, nhãn trạng thái, countdown
│   │   └── ui.js               # toast, modal, confirm, drawer, ticker
│   └── screens/                # k1..k7, l1..l3, login
└── package.json
```

## Kiểm thử tự động

```powershell
node tools/frontend_smoke.mjs        # 9 test CDP (mock): K1→K6→K7, N1→L1, duyệt, check-in, hủy lịch
node tools/live_probe.mjs            # 9 bước probe REST backend 7.1 (không cần trình duyệt)
node tools/frontend_live_smoke.mjs   # 5 test CDP (live): đặt lịch thật, OTP, duyệt, check-in, L2
node tools/frontend_shots.mjs        # chụp 7 màn vào frontend/shots/*.png (1440×900)
```

Kết quả hiện tại:
- Mock: **9/9 PASS**, 0 JS exception, 0 console error.
- Live (backend 7.1 thật): **5/5 PASS**, 0 JS exception, 0 console error.
- Probe REST: **9/9 bước PASS**.

Ảnh giao diện: `frontend/shots/k1..k4.png`, `l1..l3.png`.

## Quy tắc nghiệp vụ đã cài

- Khách tự đặt: **2 giờ – 30 ngày**; đệm ghế 5–10 phút; hồ sơ mới +15 phút.
- **Hold_Until 30 phút** cho lịch PENDING; hết hạn → L1 không cho duyệt, phải từ chối.
- Đổi/hủy của khách: **≥ 4 giờ** trước giờ hẹn; dưới 4 giờ → disable + hotline.
- OTP: 6 số, hạn 5 phút, tối đa 5 lần, gửi lại sau 60 giây.
- L1: không duyệt hàng loạt; hủy bắt buộc lý do; bác sĩ nghỉ → cảnh báo.
- L2: vãng lai → `CHECKED_IN`; cấp cứu → `isPriority`, không chiếm lịch người khác.
- L3: `CONFIRMED → CHECKED_IN`; `NO_SHOW` hoàn tác trong ngày + lý do; không cho `CHECKED_IN → CANCELLED`.
- Nhãn trạng thái đúng 5.2 §7.1 (6 trạng thái lịch), không tự thêm "Đã đổi"/"Quá hạn" thành trạng thái.

## Khác biệt so với prototype 6.2

| | Prototype 6.2 | 7.2 (bản này) |
|---|---|---|
| Dữ liệu | `localStorage` mock gắn chặt vào UI | Lớp API + MockAdapter cùng shape 5.3 |
| Điều hướng | 24 file HTML rời | SPA hash router, giữ nguyên mã màn |
| Xác thực | giả lập phiên | JWT + guard RBAC cho L1–L3 |
| Phạm vi | 24 màn low-fi | 10 màn P1 của phân hệ đặt lịch, có kiểm thử luồng |

## Ghi chú tích hợp

- Backend 7.1 dùng Response Envelope `{ success, code, message, data }` — `http.js` bóc sẵn `data`; lỗi `success=false` được đổi thành `ApiError` với `code` + `fieldErrors`.
- Mọi khác biệt field/shape nằm ở `assets/js/api/live.js`. Nếu backend đổi hợp đồng, sửa duy nhất file này.
- CORS backend hiện cho phép mọi origin (`allowedOriginPatterns("*")`) và các method `PUT/PATCH`; khi triển khai thật nên giới hạn về origin frontend.
- OTP prototype trả `prototypeOtp`; màn K5 hiện nút **Điền mã demo** (chỉ khi backend trả mã) để test nhanh. Tắt `app.otp.prototype-expose-code` khi lên production.
- L1 ở chế độ live không gửi `chairId` GH1..GH4 (dữ liệu mock); backend tự gán/giữ ghế. Muốn chọn ghế thật cần bổ sung API danh mục ghế (ngoài phạm vi 7.2).
- L3 lấy bác sĩ nghỉ từ `/api/v1/staff/leaves?status=APPROVED&fromDate=...&toDate=...` thay vì suy đoán từ lịch hẹn.
- K7 ở chế độ live bắt buộc OTP (backend lọc theo `customerId` trong JWT, không tin tham số `phone`); màn đã có form OTP rút gọn.
- Chưa nối: Zalo ZNS/SMS thật, thanh toán, hàng đợi thông báo — backend hiện chỉ ghi nhận yêu cầu gửi.
