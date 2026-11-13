-- =====================================================================
-- 02_sample_data.sql — DỮ LIỆU MẪU CHỈ DÙNG KIỂM THỬ (không dùng production)
-- Giả định: đã chạy 01_schema.sql trên database trống.
-- Dữ liệu hư cấu: họ tên, số điện thoại (09xx...), email @chia-dental.test, hash giả.
-- Dữ liệu phủ cả 24 bảng, đủ 6 trạng thái lịch hẹn, Blacklist, hoàn lại no-show,
-- đánh giá <= 3 sao (trigger tạo complaint), lịch nghỉ bác sĩ chồng lên lịch đã xác nhận.
-- Mốc thời gian lấy quanh 06/11/2026; các test Blacklist dùng cửa sổ 12 tháng so với now().
-- =====================================================================
BEGIN;

-- role_permission: đã có 3 vai trò từ 01_schema.sql

-- staff_account
INSERT INTO staff_account (staff_id, username, email, phone, full_name, password_hash, status, role_id) OVERRIDING SYSTEM VALUE VALUES
  (1, 'quanly01', 'quanly01@chia-dental.test', '0911000001', 'Nguyễn Minh Quân', '$argon2id$v=19$m=65536,t=3,p=4$MAU$KHONG_DUNG_THAT', TRUE, (SELECT role_id FROM role_permission WHERE role_name = 'MANAGER')),
  (2, 'letan01', 'letan01@chia-dental.test', '0911000002', 'Trần Thị Hà', '$argon2id$v=19$m=65536,t=3,p=4$MAU$KHONG_DUNG_THAT', TRUE, (SELECT role_id FROM role_permission WHERE role_name = 'RECEPTIONIST')),
  (3, 'letan02', 'letan02@chia-dental.test', '0911000003', 'Lê Thu Trang', '$argon2id$v=19$m=65536,t=3,p=4$MAU$KHONG_DUNG_THAT', TRUE, (SELECT role_id FROM role_permission WHERE role_name = 'RECEPTIONIST')),
  (4, 'bacsi01', 'bacsi01@chia-dental.test', '0911000004', 'BS. Phạm Văn An', '$argon2id$v=19$m=65536,t=3,p=4$MAU$KHONG_DUNG_THAT', TRUE, (SELECT role_id FROM role_permission WHERE role_name = 'DOCTOR')),
  (5, 'bacsi02', 'bacsi02@chia-dental.test', '0911000005', 'BS. Võ Thị Bích', '$argon2id$v=19$m=65536,t=3,p=4$MAU$KHONG_DUNG_THAT', TRUE, (SELECT role_id FROM role_permission WHERE role_name = 'DOCTOR')),
  (6, 'bacsi03', 'bacsi03@chia-dental.test', '0911000006', 'BS. Đặng Quốc Cường', '$argon2id$v=19$m=65536,t=3,p=4$MAU$KHONG_DUNG_THAT', TRUE, (SELECT role_id FROM role_permission WHERE role_name = 'DOCTOR'));

-- clinic_config
INSERT INTO clinic_config (config_key, config_value, config_group, updated_by) VALUES
  ('clinic_open_time', '"08:00"'::jsonb, 'booking', 1),
  ('clinic_close_time', '"18:00"'::jsonb, 'booking', 1),
  ('chair_buffer_minutes', '5'::jsonb, 'booking', 1);

-- clinic_holiday
INSERT INTO clinic_holiday (holiday_id, holiday_date, name) OVERRIDING SYSTEM VALUE VALUES
  (1, '2027-01-01', 'Tết Dương lịch'),
  (2, '2027-04-30', 'Ngày Giải phóng miền Nam'),
  (3, '2027-05-01', 'Ngày Quốc tế Lao động');

-- otp_request
INSERT INTO otp_request (otp_id, phone, otp_hash, purpose, expired_at, retry_count, locked_until, is_used, created_at) OVERRIDING SYSTEM VALUE VALUES
  (1, '0900000001', '$argon2id$MAU$OTP_MAU', 'LOGIN', '2026-10-12 08:40:00+07', 0, NULL, TRUE, '2026-10-12 08:35:00+07'),
  (2, '0900000003', '$argon2id$MAU$OTP_MAU', 'BOOKING', '2026-10-20 10:10:00+07', 3, '2026-10-20 10:30:00+07', FALSE, '2026-10-20 10:05:00+07'),
  (3, '0900000007', '$argon2id$MAU$OTP_MAU', 'LOGIN', '2026-11-06 09:10:00+07', 0, NULL, FALSE, '2026-11-06 09:05:00+07');

-- customer
INSERT INTO customer (customer_id, phone, is_blacklisted, first_consent_at, created_at) OVERRIDING SYSTEM VALUE VALUES
  (1, '0900000001', FALSE, '2026-03-01 09:00:00+07', '2026-03-01 09:00:00+07'),
  (2, '0900000002', FALSE, '2026-03-02 09:00:00+07', '2026-03-02 09:00:00+07'),
  (3, '0900000003', FALSE, '2026-03-03 09:00:00+07', '2026-03-03 09:00:00+07'),
  (4, '0900000004', FALSE, '2026-03-04 09:00:00+07', '2026-03-04 09:00:00+07'),
  (5, '0900000005', FALSE, '2026-03-05 09:00:00+07', '2026-03-05 09:00:00+07'),
  (6, '0900000006', FALSE, '2026-03-06 09:00:00+07', '2026-03-06 09:00:00+07');

-- patient_profile
INSERT INTO patient_profile (patient_id, customer_id, full_name, birth_year, dob, gender, address, medical_history) OVERRIDING SYSTEM VALUE VALUES
  (1, 1, 'Nguyễn Văn Hùng', 1985, '1985-04-12', 'MALE', '12 Lê Lợi, Vũng Tàu', decode('4d4f434b5f43495048455254455854','hex')),
  (2, 1, 'Nguyễn Gia Bảo', 2015, NULL, NULL, NULL, NULL),
  (3, 2, 'Trần Thị Mai', 1990, '1990-09-30', 'FEMALE', '45 Trần Phú, Vũng Tàu', decode('4d4f434b5f43495048455254455854','hex')),
  (4, 2, 'Trần Văn Lộc', 1962, '1962-01-20', 'MALE', '45 Trần Phú, Vũng Tàu', decode('4d4f434b5f43495048455254455854','hex')),
  (5, 3, 'Lê Hoàng Nam', 2001, '2001-06-05', 'MALE', '8 Nguyễn Thái Học, Vũng Tàu', NULL),
  (6, 3, 'Lê Thị Hoa', 1998, NULL, 'FEMALE', NULL, NULL),
  (7, 4, 'Phạm Quốc Việt', 1979, '1979-12-02', 'MALE', '101 Thùy Vân, Vũng Tàu', NULL),
  (8, 5, 'Võ Ngọc Anh', 1995, '1995-03-18', 'FEMALE', '3 Hoàng Hoa Thám, Vũng Tàu', NULL);

-- consent
INSERT INTO consent (consent_id, phone, customer_id, consent_time, channel, action, policy_version, consented_by, for_patient_id, relation, is_guardian) OVERRIDING SYSTEM VALUE VALUES
  (1, '0900000001', 1, '2026-03-01 09:00:00+07', 'Zalo', 'GIVEN', 'v1.0', 'Chính chủ', NULL, NULL, FALSE),
  (2, '0900000001', 1, '2026-03-01 09:05:00+07', 'Zalo', 'GIVEN', 'v1.0', 'Nguyễn Văn Hùng', 2, 'Con', TRUE),
  (3, '0900000002', 2, '2026-03-02 09:00:00+07', 'Zalo', 'GIVEN', 'v1.0', 'Chính chủ', NULL, NULL, FALSE),
  (4, '0900000003', 3, '2026-03-03 09:00:00+07', 'SMS', 'GIVEN', 'v1.0', 'Chính chủ', NULL, NULL, FALSE),
  (5, '0900000004', 4, '2026-03-04 09:00:00+07', 'Zalo', 'GIVEN', 'v1.0', 'Chính chủ', NULL, NULL, FALSE),
  (6, '0900000004', 4, '2026-09-01 14:00:00+07', 'Zalo', 'WITHDRAWN', 'v1.0', 'Chính chủ', NULL, NULL, FALSE),
  (7, '0900000005', 5, '2026-03-05 09:00:00+07', 'SMS', 'GIVEN', 'v1.0', 'Chính chủ', NULL, NULL, FALSE),
  (8, '0900000007', NULL, '2026-11-06 09:06:00+07', 'Zalo', 'GIVEN', 'v1.0', 'Chính chủ', NULL, NULL, FALSE);

-- opt_out
INSERT INTO opt_out (opt_out_id, customer_id, channel, excluded_notif_types, reason, is_active, created_at, created_by) OVERRIDING SYSTEM VALUE VALUES
  (1, 4, 'ALL', '["RECALL", "BIRTHDAY", "POST_VISIT_CHECK"]'::jsonb, 'Khách không muốn nhận tin chăm sóc', TRUE, '2026-09-01 14:05:00+07', 2);

-- dental_chair
INSERT INTO dental_chair (chair_id, chair_name, status) OVERRIDING SYSTEM VALUE VALUES
  (1, 'Ghế 01', 'ACTIVE'),
  (2, 'Ghế 02', 'ACTIVE'),
  (3, 'Ghế 03', 'MAINTENANCE');

-- care_scenario
INSERT INTO care_scenario (scenario_id, category, followup_min_days, followup_max_days, message_template) OVERRIDING SYSTEM VALUE VALUES
  (1, 'GENERAL', 1, 3, 'Cảm ơn quý khách đã khám tại CHIA. Nếu có bất thường, vui lòng liên hệ phòng khám.'),
  (2, 'MINOR_SURGERY', 1, 2, 'Phòng khám hỏi thăm tình trạng vết thương sau tiểu phẫu của quý khách.'),
  (3, 'PROSTHETIC', 1, 3, 'Phòng khám hỏi thăm cảm giác ăn nhai với phục hình của quý khách.'),
  (4, 'LONG_TERM', 1, 3, 'Nhắc quý khách duy trì lịch tái khám theo liệu trình.');

-- service
INSERT INTO service (service_id, name, duration, price, care_scenario_id, followup_cycle_days, is_active) OVERRIDING SYSTEM VALUE VALUES
  (1, 'Khám tổng quát', 30, 150000, 1, NULL, TRUE),
  (2, 'Cạo vôi răng', 30, 300000, 1, 180, TRUE),
  (3, 'Trám răng', 45, 400000, 1, NULL, TRUE),
  (4, 'Nhổ răng khôn', 60, 1500000, 2, NULL, TRUE),
  (5, 'Bọc răng sứ', 90, 4500000, 3, NULL, TRUE),
  (6, 'Niềng răng - tái khám', 30, 800000, 4, 30, TRUE),
  (7, 'Tẩy trắng răng', 60, 2000000, NULL, NULL, TRUE),
  (8, 'Tư vấn', 15, NULL, NULL, NULL, TRUE);

-- doctor_schedule
INSERT INTO doctor_schedule (schedule_id, staff_id, shift_date, start_time, end_time, status) OVERRIDING SYSTEM VALUE VALUES
  (1, 4, '2026-11-09', '08:00', '17:00', 'ACTIVE'),
  (2, 4, '2026-11-10', '08:00', '17:00', 'ACTIVE'),
  (3, 4, '2026-11-12', '08:00', '17:00', 'ACTIVE'),
  (4, 4, '2026-11-20', '08:00', '17:00', 'ACTIVE'),
  (5, 5, '2026-11-09', '08:00', '17:00', 'ACTIVE'),
  (6, 5, '2026-11-10', '08:00', '17:00', 'ACTIVE'),
  (7, 5, '2026-11-12', '08:00', '17:00', 'ACTIVE'),
  (8, 5, '2026-11-20', '08:00', '17:00', 'ACTIVE'),
  (9, 6, '2026-11-09', '08:00', '17:00', 'ACTIVE'),
  (10, 6, '2026-11-10', '08:00', '17:00', 'ACTIVE'),
  (11, 6, '2026-11-12', '08:00', '17:00', 'ACTIVE'),
  (12, 6, '2026-11-20', '08:00', '17:00', 'ACTIVE');

-- doctor_leave
INSERT INTO doctor_leave (leave_id, staff_id, leave_date, start_time, end_time, reason, status, approved_by, approved_at) OVERRIDING SYSTEM VALUE VALUES
  (1, 5, '2026-11-10', '09:00', '12:00', 'Việc gia đình đột xuất', 'APPROVED', 1, '2026-11-06 08:30:00+07'),
  (2, 6, '2026-11-20', '13:00', '17:00', 'Dự hội thảo', 'PENDING', NULL, NULL);

-- appointment
INSERT INTO appointment (appointment_id, patient_id, staff_id, chair_id, date_time, duration, status, source, booked_by_phone, hold_until, cancelled_by, cancelled_by_type, cancel_reason, actual_arrival_at, is_priority, note, created_at, created_by) OVERRIDING SYSTEM VALUE VALUES
  (1, 1, 4, 1, '2026-10-12 09:00:00+07', 50, 'COMPLETED', 'ONLINE', '0900000001', NULL, NULL, NULL, NULL, '2026-10-12 08:55:00+07', FALSE, NULL, '2026-10-10 09:00:00+07', NULL),
  (2, 2, 5, 2, '2026-10-15 10:00:00+07', 35, 'COMPLETED', 'PHONE', '0900000001', NULL, NULL, NULL, NULL, '2026-10-15 09:58:00+07', FALSE, NULL, '2026-10-13 10:00:00+07', 2),
  (3, 3, 4, 1, '2026-10-18 14:00:00+07', 80, 'COMPLETED', 'ONLINE', '0900000002', NULL, NULL, NULL, NULL, '2026-10-18 13:50:00+07', FALSE, NULL, '2026-10-16 14:00:00+07', NULL),
  (4, 4, 6, 1, '2026-10-20 15:00:00+07', 110, 'COMPLETED', 'WALK_IN', '0900000002', NULL, NULL, NULL, NULL, '2026-10-20 15:00:00+07', FALSE, NULL, '2026-10-20 14:50:00+07', 3),
  (5, 5, 4, 2, '2026-08-20 09:00:00+07', 35, 'COMPLETED', 'PHONE', '0900000003', NULL, NULL, NULL, NULL, '2026-08-20 09:02:00+07', FALSE, NULL, '2026-08-18 09:00:00+07', 2),
  (6, 6, 5, 1, '2026-09-15 09:00:00+07', 35, 'NO_SHOW', 'PHONE', '0900000003', NULL, NULL, NULL, NULL, NULL, FALSE, NULL, '2026-09-13 09:00:00+07', 2),
  (7, 5, 5, 1, '2026-09-22 09:00:00+07', 35, 'NO_SHOW', 'PHONE', '0900000003', NULL, NULL, NULL, NULL, NULL, FALSE, NULL, '2026-09-20 09:00:00+07', 2),
  (8, 6, 5, 1, '2026-10-02 09:00:00+07', 35, 'NO_SHOW', 'PHONE', '0900000003', NULL, NULL, NULL, NULL, NULL, FALSE, NULL, '2026-09-30 09:00:00+07', 2),
  (9, 8, 6, 2, '2026-10-05 09:00:00+07', 50, 'COMPLETED', 'PHONE', '0900000005', NULL, NULL, NULL, NULL, '2026-10-05 09:40:00+07', FALSE, NULL, '2026-10-03 09:00:00+07', 2),
  (10, 7, 6, 2, '2026-11-03 10:00:00+07', 50, 'CANCELLED', 'ONLINE', '0900000004', NULL, NULL, 'CUSTOMER', 'Khách bận việc đột xuất', NULL, FALSE, NULL, '2026-11-01 10:00:00+07', NULL),
  (11, 8, 4, 2, '2026-11-04 11:00:00+07', 35, 'CANCELLED', 'PHONE', '0900000005', NULL, 2, 'STAFF', 'Bác sĩ đổi ca, đã báo khách', NULL, FALSE, NULL, '2026-11-02 11:00:00+07', 2),
  (12, 7, 4, 1, '2026-11-07 09:00:00+07', 50, 'PENDING', 'ONLINE', '0900000004', '2026-11-06 20:00:00+07', NULL, NULL, NULL, NULL, FALSE, 'Đau răng hàm trên bên phải', '2026-11-06 08:00:00+07', NULL),
  (13, 1, 5, 2, '2026-11-10 09:00:00+07', 50, 'CONFIRMED', 'ONLINE', '0900000001', NULL, NULL, NULL, NULL, NULL, FALSE, 'Trám răng số 6', '2026-11-08 09:00:00+07', NULL),
  (14, 3, 4, 1, '2026-11-12 14:00:00+07', 35, 'CONFIRMED', 'PHONE', '0900000002', NULL, NULL, NULL, NULL, NULL, FALSE, NULL, '2026-11-10 14:00:00+07', 2),
  (15, 4, 6, 1, '2026-11-06 09:00:00+07', 35, 'CHECKED_IN', 'WALK_IN', '0900000002', NULL, NULL, NULL, NULL, '2026-11-06 09:00:00+07', TRUE, 'Đau nhức dữ dội, cần xử lý gấp', '2026-11-06 08:50:00+07', 3),
  (16, 2, 4, 2, '2026-11-20 10:00:00+07', 65, 'CONFIRMED', 'ONLINE', '0900000001', NULL, NULL, NULL, NULL, NULL, FALSE, NULL, '2026-11-18 10:00:00+07', NULL);

-- appointment_service
INSERT INTO appointment_service (appt_service_id, appointment_id, service_id, is_temporary, duration_snapshot, price_snapshot, is_performed) OVERRIDING SYSTEM VALUE VALUES
  (1, 1, 1, FALSE, 30, 150000, TRUE),
  (2, 2, 2, FALSE, 30, 300000, TRUE),
  (3, 3, 4, FALSE, 60, 1500000, TRUE),
  (4, 4, 5, FALSE, 90, 4500000, TRUE),
  (5, 5, 6, FALSE, 30, 800000, TRUE),
  (6, 6, 1, FALSE, 30, 150000, FALSE),
  (7, 7, 6, FALSE, 30, 800000, FALSE),
  (8, 8, 2, FALSE, 30, 300000, FALSE),
  (9, 9, 3, FALSE, 45, 400000, TRUE),
  (10, 10, 1, FALSE, 30, 150000, FALSE),
  (11, 11, 2, FALSE, 30, 300000, FALSE),
  (12, 12, 1, FALSE, 30, 150000, FALSE),
  (13, 13, 3, FALSE, 45, 400000, FALSE),
  (14, 14, 2, FALSE, 30, 300000, FALSE),
  (15, 15, NULL, TRUE, 30, NULL, FALSE),
  (16, 16, 7, FALSE, 60, 2000000, FALSE);

-- treatment_record
INSERT INTO treatment_record (record_id, appointment_id, patient_id, staff_id, clinical_note, treatment_result, next_appointment_date, created_at) OVERRIDING SYSTEM VALUE VALUES
  (1, 1, 1, 4, 'Răng sạch, không sâu. Hồ sơ mới.', 'Khám tổng quát hoàn tất.', NULL, '2026-10-01 10:00:00+07'),
  (2, 2, 2, 5, 'Cao răng vừa, đã làm sạch.', 'Hoàn tất cạo vôi.', '2027-04-15', '2026-10-01 10:00:00+07'),
  (3, 3, 3, 4, 'Răng khôn hàm dưới trái mọc lệch.', 'Đã nhổ, dặn kiêng.', NULL, '2026-10-01 10:00:00+07'),
  (4, 4, 4, 6, 'Mài răng 36, lấy dấu.', 'Gắn răng sứ tạm.', '2026-11-03', '2026-10-01 10:00:00+07'),
  (5, 5, 5, 4, 'Siết dây cung.', 'Tiến triển tốt.', '2026-09-20', '2026-10-01 10:00:00+07'),
  (6, 9, 8, 6, 'Trám răng 25.', 'Hoàn tất.', NULL, '2026-10-01 10:00:00+07');

-- treatment_progress
INSERT INTO treatment_progress (progress_id, patient_id, service_id, start_date, expected_end, current_session, total_sessions, status) OVERRIDING SYSTEM VALUE VALUES
  (1, 5, 6, '2026-03-01', '2027-09-01', 8, 18, 'IN_PROGRESS'),
  (2, 3, 5, '2026-10-20', NULL, 1, 3, 'PAUSED');

-- care_task
INSERT INTO care_task (task_id, appointment_id, patient_id, task_type, source, care_scenario_id, assignee_id, created_at, scheduled_at, sla_deadline, status, is_overdue, result_note, completed_at) OVERRIDING SYSTEM VALUE VALUES
  (1, 3, 3, 'POST_VISIT_CHECK', 'SYSTEM', 2, 2, '2026-10-18 16:00:00+07', '2026-10-19 16:00:00+07', '2026-10-21 16:00:00+07', 'WAITING', TRUE, NULL, NULL),
  (2, 14, 3, 'CONFIRM_CALL', 'MANUAL', NULL, 3, '2026-11-06 08:00:00+07', '2026-11-10 09:00:00+07', '2026-11-11 09:00:00+07', 'CLOSED', FALSE, 'Đã gọi xác nhận, khách đồng ý', '2026-11-06 10:00:00+07'),
  (3, 1, 1, 'RECALL', 'SYSTEM', 1, 2, '2026-10-12 10:00:00+07', '2027-04-12 09:00:00+07', '2027-04-14 09:00:00+07', 'WAITING', FALSE, NULL, NULL),
  (4, 4, 4, 'POST_VISIT_CHECK', 'SYSTEM', 3, 2, '2026-10-20 17:00:00+07', '2026-10-23 09:00:00+07', '2026-10-25 09:00:00+07', 'CLOSED', FALSE, 'Khách ổn, không đau', '2026-10-23 10:00:00+07');

-- feedback: trigger đặt alert_flag và tự tạo complaint khi rating_score <= 3

-- feedback
INSERT INTO feedback (feedback_id, appointment_id, customer_id, patient_id, staff_id, rating_score, comment, created_at) OVERRIDING SYSTEM VALUE VALUES
  (1, 1, 1, 1, 4, 5, 'Bác sĩ nhẹ nhàng, phòng khám sạch.', '2026-10-12 12:00:00+07'),
  (2, 2, 1, 2, 5, 2, 'Chờ lâu hơn giờ hẹn, nhân viên chưa nhiệt tình.', '2026-10-15 12:00:00+07'),
  (3, 4, 2, 4, 6, 4, 'Hài lòng, mong thời gian chờ ngắn hơn.', '2026-10-20 18:00:00+07');

-- complaint #1 do trigger sinh từ feedback #2 (đánh giá 2 sao): phân công và cập nhật xử lý
UPDATE complaint SET handler_id = 2, status = 'IN_PROGRESS', severity = 'MEDIUM',
       action_taken = 'Lễ tân gọi xin lỗi khách, ghi nhận phản hồi về thời gian chờ'
 WHERE feedback_id = 2;
-- complaint #2 nhập tay, không gắn đánh giá, đã đóng
INSERT INTO complaint (patient_id, feedback_id, content, severity, handler_id, action_taken, result, status, created_at, closed_at)
VALUES (4, NULL, 'Răng sứ tạm bị bong sau 2 ngày', 'HIGH', 1, 'Hẹn khách quay lại gắn lại răng', 'Đã gắn lại, khách hài lòng',
        'CLOSED', '2026-10-23 09:00:00+07', '2026-10-24 10:00:00+07');

-- audit_log
INSERT INTO audit_log (actor_type, actor_staff_id, actor_customer_id, action_type, target_entity, target_id, old_value, new_value, ip_address, event_time) VALUES
  ('STAFF', 1, NULL, 'ACCOUNT_CREATE', 'staff_account', '4', NULL, '{"username": "bacsi01", "role": "DOCTOR"}'::jsonb, '10.0.0.5'::inet, '2026-03-01 08:00:00+07'),
  ('CUSTOMER', NULL, 4, 'APPOINTMENT_CANCEL', 'appointment', '10', '{"status": "CONFIRMED"}'::jsonb, '{"status": "CANCELLED"}'::jsonb, '203.0.113.10'::inet, '2026-11-02 20:15:00+07'),
  ('SYSTEM', NULL, NULL, 'APPOINTMENT_AUTO_CANCEL', 'appointment', '99', '{"status": "PENDING"}'::jsonb, '{"status": "CANCELLED"}'::jsonb, NULL, '2026-11-01 07:00:00+07'),
  ('STAFF', 2, NULL, 'APPOINTMENT_RESCHEDULE', 'appointment', '14', '{"date_time": "2026-11-11T14:00"}'::jsonb, '{"date_time": "2026-11-12T14:00"}'::jsonb, '10.0.0.7'::inet, '2026-11-05 15:00:00+07'),
  ('STAFF', 1, NULL, 'CONFIG_UPDATE', 'clinic_config', 'blacklist_threshold', '2'::jsonb, '3'::jsonb, '10.0.0.5'::inet, '2026-04-01 09:00:00+07');

-- notification_log
INSERT INTO notification_log (notif_id, customer_id, appointment_id, care_task_id, notif_type, channel, recipient_phone, content, status, error_code, provider_msg_id, created_at, sent_at) OVERRIDING SYSTEM VALUE VALUES
  (1, NULL, NULL, NULL, 'OTP', 'SMS', '0900000007', 'Ma xac thuc cua ban la ******', 'SUCCESS', NULL, 'MSG0001', '2026-11-06 09:05:00+07', '2026-11-06 09:05:00+07'),
  (2, 2, 3, NULL, 'APPOINTMENT_CONFIRMED', 'ZALO_ZNS', '0900000002', 'Lich hen cua ban da duoc xac nhan', 'SUCCESS', NULL, 'MSG0002', '2026-10-16 10:00:00+07', '2026-10-16 10:00:00+07'),
  (3, 2, 14, NULL, 'APPOINTMENT_REMINDER', 'ZALO_ZNS', '0900000002', 'Nhac lich hen 24h', 'FAILED', 'ZNS_USER_NOT_FOUND', NULL, '2026-11-11 14:00:00+07', NULL),
  (4, 2, 14, NULL, 'APPOINTMENT_REMINDER', 'SMS', '0900000002', 'Nhac lich hen 24h', 'SUCCESS', NULL, 'MSG0004', '2026-11-11 14:01:00+07', '2026-11-11 14:01:00+07'),
  (5, 2, 3, 1, 'POST_VISIT_CHECK', 'ZALO_ZNS', '0900000002', 'Hoi tham sau tieu phau', 'SUCCESS', NULL, 'MSG0005', '2026-10-19 16:00:00+07', '2026-10-19 16:00:00+07');

-- no_show_event
INSERT INTO no_show_event (event_id, patient_id, appointment_id, occurred_at, is_reversed, reversed_by, reversed_at, reversed_reason) OVERRIDING SYSTEM VALUE VALUES
  (1, 6, 6, '2026-09-15 09:30:00+07', FALSE, NULL, NULL, NULL),
  (2, 5, 7, '2026-09-22 09:30:00+07', FALSE, NULL, NULL, NULL),
  (3, 6, 8, '2026-10-02 09:30:00+07', FALSE, NULL, NULL, NULL),
  (4, 8, 9, '2026-10-05 09:16:00+07', TRUE, 2, '2026-10-05 09:40:00+07', 'Khách đến trễ 40 phút do kẹt xe, lễ tân xác nhận');

-- Đồng bộ sequence IDENTITY sau khi chèn ID tường minh

SELECT setval(pg_get_serial_sequence('role_permission', 'role_id'), (SELECT COALESCE(MAX(role_id), 1) FROM role_permission));
SELECT setval(pg_get_serial_sequence('staff_account', 'staff_id'), (SELECT COALESCE(MAX(staff_id), 1) FROM staff_account));
SELECT setval(pg_get_serial_sequence('clinic_holiday', 'holiday_id'), (SELECT COALESCE(MAX(holiday_id), 1) FROM clinic_holiday));
SELECT setval(pg_get_serial_sequence('otp_request', 'otp_id'), (SELECT COALESCE(MAX(otp_id), 1) FROM otp_request));
SELECT setval(pg_get_serial_sequence('customer', 'customer_id'), (SELECT COALESCE(MAX(customer_id), 1) FROM customer));
SELECT setval(pg_get_serial_sequence('patient_profile', 'patient_id'), (SELECT COALESCE(MAX(patient_id), 1) FROM patient_profile));
SELECT setval(pg_get_serial_sequence('consent', 'consent_id'), (SELECT COALESCE(MAX(consent_id), 1) FROM consent));
SELECT setval(pg_get_serial_sequence('opt_out', 'opt_out_id'), (SELECT COALESCE(MAX(opt_out_id), 1) FROM opt_out));
SELECT setval(pg_get_serial_sequence('dental_chair', 'chair_id'), (SELECT COALESCE(MAX(chair_id), 1) FROM dental_chair));
SELECT setval(pg_get_serial_sequence('care_scenario', 'scenario_id'), (SELECT COALESCE(MAX(scenario_id), 1) FROM care_scenario));
SELECT setval(pg_get_serial_sequence('service', 'service_id'), (SELECT COALESCE(MAX(service_id), 1) FROM service));
SELECT setval(pg_get_serial_sequence('doctor_schedule', 'schedule_id'), (SELECT COALESCE(MAX(schedule_id), 1) FROM doctor_schedule));
SELECT setval(pg_get_serial_sequence('doctor_leave', 'leave_id'), (SELECT COALESCE(MAX(leave_id), 1) FROM doctor_leave));
SELECT setval(pg_get_serial_sequence('appointment', 'appointment_id'), (SELECT COALESCE(MAX(appointment_id), 1) FROM appointment));
SELECT setval(pg_get_serial_sequence('appointment_service', 'appt_service_id'), (SELECT COALESCE(MAX(appt_service_id), 1) FROM appointment_service));
SELECT setval(pg_get_serial_sequence('treatment_record', 'record_id'), (SELECT COALESCE(MAX(record_id), 1) FROM treatment_record));
SELECT setval(pg_get_serial_sequence('treatment_progress', 'progress_id'), (SELECT COALESCE(MAX(progress_id), 1) FROM treatment_progress));
SELECT setval(pg_get_serial_sequence('care_task', 'task_id'), (SELECT COALESCE(MAX(task_id), 1) FROM care_task));
SELECT setval(pg_get_serial_sequence('feedback', 'feedback_id'), (SELECT COALESCE(MAX(feedback_id), 1) FROM feedback));
SELECT setval(pg_get_serial_sequence('audit_log', 'log_id'), (SELECT COALESCE(MAX(log_id), 1) FROM audit_log));
SELECT setval(pg_get_serial_sequence('notification_log', 'notif_id'), (SELECT COALESCE(MAX(notif_id), 1) FROM notification_log));
SELECT setval(pg_get_serial_sequence('no_show_event', 'event_id'), (SELECT COALESCE(MAX(event_id), 1) FROM no_show_event));

COMMIT;
