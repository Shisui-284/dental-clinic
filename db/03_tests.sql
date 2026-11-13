-- =====================================================================
-- 03_tests.sql — Kiểm tra CRUD, ràng buộc và trigger (chạy SAU 01_schema.sql và 02_sample_data.sql)
-- Toàn bộ chạy trong 1 transaction và ROLLBACK ở cuối: không làm thay đổi dữ liệu mẫu.
-- Kết quả: bảng test_result ở cuối (cột passed = true/false); psql thoát mã lỗi nếu có test FAIL.
-- =====================================================================
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE test_result (seq SERIAL, test_name TEXT, passed BOOLEAN, detail TEXT);

-- Mong đợi câu lệnh chạy thành công
CREATE FUNCTION pg_temp.expect_ok(p_name TEXT, p_sql TEXT) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE p_sql;
    INSERT INTO test_result(test_name, passed) VALUES (p_name, TRUE);
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO test_result(test_name, passed, detail) VALUES (p_name, FALSE, SQLSTATE || ' ' || SQLERRM);
  END;
END $$;

-- Mong đợi câu lệnh lỗi với SQLSTATE chỉ định (23502 NOT NULL, 23503 FK, 23505 UNIQUE, 23514 CHECK, 23P01 EXCLUDE, P0001 trigger)
CREATE FUNCTION pg_temp.expect_error(p_name TEXT, p_sql TEXT, p_state TEXT) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE p_sql;
  EXCEPTION WHEN OTHERS THEN
    IF SQLSTATE = p_state THEN
      INSERT INTO test_result(test_name, passed) VALUES (p_name, TRUE);
    ELSE
      INSERT INTO test_result(test_name, passed, detail)
      VALUES (p_name, FALSE, 'mong đợi ' || p_state || ', nhận ' || SQLSTATE || ' ' || SQLERRM);
    END IF;
    RETURN;
  END;
  INSERT INTO test_result(test_name, passed, detail) VALUES (p_name, FALSE, 'mong đợi lỗi ' || p_state || ' nhưng câu lệnh thành công');
END $$;

CREATE FUNCTION pg_temp.expect_true(p_name TEXT, p_cond BOOLEAN) RETURNS void LANGUAGE sql AS $$
  INSERT INTO test_result(test_name, passed, detail) VALUES (p_name, COALESCE(p_cond, FALSE), CASE WHEN COALESCE(p_cond, FALSE) THEN NULL ELSE 'điều kiện sai/NULL' END);
$$;

-- ---------------------------------------------------------------- A. CRUD cơ bản
SELECT pg_temp.expect_ok  ('A01 CREATE customer',  $q$INSERT INTO customer (phone) VALUES ('0900009999')$q$);
SELECT pg_temp.expect_true('A02 READ customer',    EXISTS (SELECT 1 FROM customer WHERE phone = '0900009999'));
SELECT pg_temp.expect_ok  ('A03 UPDATE customer',  $q$UPDATE customer SET first_consent_at = now() WHERE phone = '0900009999'$q$);
SELECT pg_temp.expect_true('A04 UPDATE có hiệu lực', EXISTS (SELECT 1 FROM customer WHERE phone = '0900009999' AND first_consent_at IS NOT NULL));
SELECT pg_temp.expect_ok  ('A05 CREATE patient_profile', $q$INSERT INTO patient_profile (customer_id, full_name, birth_year, dob)
        SELECT customer_id, 'Bệnh nhân thử', 1990, DATE '1990-05-05' FROM customer WHERE phone = '0900009999'$q$);
SELECT pg_temp.expect_error('A06 DELETE customer còn hồ sơ bệnh nhân bị chặn bởi FK', $q$DELETE FROM customer WHERE phone = '0900009999'$q$, '23503');
SELECT pg_temp.expect_ok  ('A07 DELETE patient_profile', $q$DELETE FROM patient_profile WHERE full_name = 'Bệnh nhân thử'$q$);
SELECT pg_temp.expect_ok  ('A08 DELETE customer', $q$DELETE FROM customer WHERE phone = '0900009999'$q$);
SELECT pg_temp.expect_true('A09 DELETE có hiệu lực', NOT EXISTS (SELECT 1 FROM customer WHERE phone = '0900009999'));

SELECT pg_temp.expect_ok  ('A10 CREATE appointment', $q$INSERT INTO appointment (patient_id, staff_id, chair_id, date_time, duration, status, source, created_by)
        VALUES (1, 4, 1, '2026-12-01 09:00+07', 30, 'CONFIRMED', 'PHONE', 2)$q$);
SELECT pg_temp.expect_true('A11 end_time = date_time + duration', EXISTS (SELECT 1 FROM appointment
        WHERE date_time = '2026-12-01 09:00+07' AND staff_id = 4 AND end_time = '2026-12-01 09:30+07'));
SELECT pg_temp.expect_ok  ('A12 UPDATE appointment -> CHECKED_IN', $q$UPDATE appointment SET status = 'CHECKED_IN', actual_arrival_at = '2026-12-01 08:58+07'
        WHERE date_time = '2026-12-01 09:00+07' AND staff_id = 4$q$);
SELECT pg_temp.expect_ok  ('A13 UPDATE appointment -> CANCELLED (khách hủy)', $q$UPDATE appointment SET status = 'CANCELLED', cancelled_by_type = 'CUSTOMER', cancel_reason = 'test'
        WHERE date_time = '2026-12-01 09:00+07' AND staff_id = 4$q$);
SELECT pg_temp.expect_ok  ('A14 DELETE appointment', $q$DELETE FROM appointment WHERE date_time = '2026-12-01 09:00+07' AND staff_id = 4$q$);

SELECT pg_temp.expect_ok  ('A15 CREATE service', $q$INSERT INTO service (name, duration, price) VALUES ('Dịch vụ thử', 20, 100000)$q$);
SELECT pg_temp.expect_ok  ('A16 UPDATE service', $q$UPDATE service SET price = 120000 WHERE name = 'Dịch vụ thử'$q$);
SELECT pg_temp.expect_ok  ('A17 DELETE service', $q$DELETE FROM service WHERE name = 'Dịch vụ thử'$q$);

-- ---------------------------------------------------------------- B. NOT NULL / UNIQUE / FK
SELECT pg_temp.expect_error('B01 NOT NULL: customer.phone', $q$INSERT INTO customer (phone) VALUES (NULL)$q$, '23502');
SELECT pg_temp.expect_error('B02 UNIQUE: customer.phone trùng', $q$INSERT INTO customer (phone) VALUES ('0900000001')$q$, '23505');
SELECT pg_temp.expect_error('B03 UNIQUE: staff_account.email trùng', $q$INSERT INTO staff_account (username,email,phone,full_name,password_hash,role_id)
        VALUES ('x1','letan01@chia-dental.test','0911999991','X','h',1)$q$, '23505');
SELECT pg_temp.expect_error('B04 UNIQUE: service.name trùng', $q$INSERT INTO service (name,duration) VALUES ('Khám tổng quát',10)$q$, '23505');
SELECT pg_temp.expect_error('B05 FK: appointment.patient_id không tồn tại', $q$INSERT INTO appointment (patient_id,staff_id,chair_id,date_time,duration,source,created_by,status)
        VALUES (9999,4,1,'2026-12-02 09:00+07',30,'PHONE',2,'CONFIRMED')$q$, '23503');
SELECT pg_temp.expect_error('B06 FK: patient_profile.customer_id không tồn tại', $q$INSERT INTO patient_profile (customer_id,full_name,birth_year) VALUES (9999,'X',1990)$q$, '23503');
SELECT pg_temp.expect_error('B07 FK: không xóa staff đang được tham chiếu', $q$DELETE FROM staff_account WHERE staff_id = 4$q$, '23503');
SELECT pg_temp.expect_error('B08 UNIQUE: mỗi lịch tối đa 1 treatment_record', $q$INSERT INTO treatment_record (appointment_id,patient_id,staff_id) VALUES (1,1,4)$q$, '23505');
SELECT pg_temp.expect_error('B09 UNIQUE: mỗi lịch tối đa 1 đánh giá', $q$INSERT INTO feedback (appointment_id,customer_id,patient_id,staff_id,rating_score) VALUES (1,1,1,4,5)$q$, '23505');
SELECT pg_temp.expect_error('B10 UNIQUE: mỗi lịch tối đa 1 no_show_event', $q$INSERT INTO no_show_event (patient_id,appointment_id) VALUES (6,6)$q$, '23505');
SELECT pg_temp.expect_error('B11 FK ghép: feedback sai bệnh nhân so với lịch hẹn', $q$INSERT INTO feedback (appointment_id,customer_id,patient_id,staff_id,rating_score) VALUES (3,1,1,4,5)$q$, '23503');
SELECT pg_temp.expect_error('B12 FK ghép: treatment_record sai bệnh nhân so với lịch hẹn', $q$INSERT INTO treatment_record (appointment_id,patient_id,staff_id) VALUES (16,5,4)$q$, '23503');
SELECT pg_temp.expect_error('B13 UNIQUE: dịch vụ trùng trong 1 lịch', $q$INSERT INTO appointment_service (appointment_id,service_id,duration_snapshot) VALUES (1,1,30)$q$, '23505');

-- ---------------------------------------------------------------- C. CHECK
SELECT pg_temp.expect_error('C01 CHECK: định dạng số điện thoại', $q$INSERT INTO customer (phone) VALUES ('abc')$q$, '23514');
SELECT pg_temp.expect_error('C02 CHECK: appointment.status ngoài enum', $q$UPDATE appointment SET status = 'DONE' WHERE appointment_id = 16$q$, '23514');
SELECT pg_temp.expect_error('C03 CHECK: appointment.source ngoài enum', $q$UPDATE appointment SET source = 'FAX' WHERE appointment_id = 16$q$, '23514');
SELECT pg_temp.expect_error('C04 CHECK: duration <= 0', $q$UPDATE appointment SET duration = 0 WHERE appointment_id = 16$q$, '23514');
SELECT pg_temp.expect_error('C05 CHECK: PENDING phải có hold_until', $q$UPDATE appointment SET hold_until = NULL WHERE appointment_id = 12$q$, '23514');
SELECT pg_temp.expect_error('C06 CHECK: hold_until <= date_time', $q$UPDATE appointment SET hold_until = '2026-11-08 00:00+07' WHERE appointment_id = 12$q$, '23514');
SELECT pg_temp.expect_error('C07 CHECK: CANCELLED phải có cancelled_by_type', $q$UPDATE appointment SET status = 'CANCELLED' WHERE appointment_id = 16$q$, '23514');
SELECT pg_temp.expect_error('C08 CHECK: cancelled_by chỉ khi STAFF hủy', $q$UPDATE appointment SET cancelled_by = 2 WHERE appointment_id = 10$q$, '23514');
SELECT pg_temp.expect_error('C09 CHECK: CHECKED_IN phải có actual_arrival_at', $q$UPDATE appointment SET status = 'CHECKED_IN' WHERE appointment_id = 16$q$, '23514');
SELECT pg_temp.expect_error('C10 CHECK: lịch không phải ONLINE phải có created_by', $q$UPDATE appointment SET created_by = NULL WHERE appointment_id = 14$q$, '23514');
SELECT pg_temp.expect_error('C11 CHECK: rating_score ngoài 1–5 (6)', $q$INSERT INTO feedback (appointment_id,customer_id,patient_id,staff_id,rating_score) VALUES (3,2,3,4,6)$q$, '23514');
SELECT pg_temp.expect_error('C12 CHECK: rating_score ngoài 1–5 (0)', $q$INSERT INTO feedback (appointment_id,customer_id,patient_id,staff_id,rating_score) VALUES (3,2,3,4,0)$q$, '23514');
SELECT pg_temp.expect_error('C13 CHECK: ẩn phản hồi phải có hidden_by/hidden_at', $q$UPDATE feedback SET is_hidden = TRUE WHERE feedback_id = 1$q$, '23514');
SELECT pg_temp.expect_error('C14 CHECK: birth_year ngoài khoảng', $q$INSERT INTO patient_profile (customer_id,full_name,birth_year) VALUES (1,'X',1800)$q$, '23514');
SELECT pg_temp.expect_error('C15 CHECK: dob không khớp birth_year', $q$INSERT INTO patient_profile (customer_id,full_name,birth_year,dob) VALUES (1,'X',1990,'1985-01-01')$q$, '23514');
SELECT pg_temp.expect_error('C16 CHECK: service.duration <= 0', $q$INSERT INTO service (name,duration) VALUES ('Sai',0)$q$, '23514');
SELECT pg_temp.expect_error('C17 CHECK: service.price âm', $q$INSERT INTO service (name,duration,price) VALUES ('Sai2',10,-1)$q$, '23514');
SELECT pg_temp.expect_error('C18 CHECK: opt_out không được loại trừ OTP/xác nhận lịch', $q$INSERT INTO opt_out (customer_id,channel,excluded_notif_types,created_by) VALUES (5,'ALL','["OTP"]',2)$q$, '23514');
SELECT pg_temp.expect_error('C19 CHECK: opt_out.channel ngoài enum', $q$INSERT INTO opt_out (customer_id,channel,created_by) VALUES (5,'EMAIL',2)$q$, '23514');
SELECT pg_temp.expect_error('C20 CHECK: notification OTP mới được thiếu customer', $q$INSERT INTO notification_log (customer_id,notif_type,channel,recipient_phone,content,status,sent_at)
        VALUES (NULL,'RECALL','SMS','0900000001','x','SUCCESS',now())$q$, '23514');
SELECT pg_temp.expect_error('C21 CHECK: notification FAILED phải có error_code', $q$INSERT INTO notification_log (customer_id,notif_type,channel,recipient_phone,content,status)
        VALUES (1,'RECALL','SMS','0900000001','x','FAILED')$q$, '23514');
SELECT pg_temp.expect_error('C22 CHECK: notification channel ngoài Zalo/SMS', $q$INSERT INTO notification_log (customer_id,notif_type,channel,recipient_phone,content,status,sent_at)
        VALUES (1,'RECALL','EMAIL','0900000001','x','SUCCESS',now())$q$, '23514');
SELECT pg_temp.expect_error('C23 CHECK: care_task CLOSED phải có completed_at', $q$UPDATE care_task SET status = 'CLOSED', completed_at = NULL WHERE task_id = 1$q$, '23514');
SELECT pg_temp.expect_error('C24 CHECK: care_task.task_type ngoài enum', $q$UPDATE care_task SET task_type = 'OTHER' WHERE task_id = 1$q$, '23514');
SELECT pg_temp.expect_error('C25 CHECK: sla_deadline >= created_at', $q$UPDATE care_task SET sla_deadline = created_at - interval '1 hour' WHERE task_id = 1$q$, '23514');
SELECT pg_temp.expect_error('C26 CHECK: current_session <= total_sessions', $q$UPDATE treatment_progress SET current_session = 99 WHERE progress_id = 1$q$, '23514');
SELECT pg_temp.expect_error('C27 CHECK: complaint CLOSED phải có result/closed_at', $q$UPDATE complaint SET status = 'CLOSED' WHERE feedback_id = 2$q$, '23514');
SELECT pg_temp.expect_error('C28 CHECK: leave APPROVED phải có approved_by/at', $q$UPDATE doctor_leave SET status = 'APPROVED' WHERE leave_id = 2$q$, '23514');
SELECT pg_temp.expect_error('C29 CHECK: doctor_leave end_time > start_time', $q$INSERT INTO doctor_leave (staff_id,leave_date,start_time,end_time,reason) VALUES (4,'2026-12-01','12:00','10:00','x')$q$, '23514');
SELECT pg_temp.expect_error('C30 CHECK: appointment_service chỉ được thiếu service_id khi is_temporary', $q$INSERT INTO appointment_service (appointment_id,service_id,is_temporary,duration_snapshot) VALUES (16,NULL,FALSE,30)$q$, '23514');
SELECT pg_temp.expect_ok   ('C31 OK: dịch vụ tạm thời được phép service_id NULL', $q$INSERT INTO appointment_service (appointment_id,service_id,is_temporary,duration_snapshot) VALUES (16,NULL,TRUE,30)$q$);
SELECT pg_temp.expect_error('C32 CHECK: audit_log actor_type STAFF phải có actor_staff_id', $q$INSERT INTO audit_log (actor_type,action_type,target_entity,target_id) VALUES ('STAFF','X','appointment','1')$q$, '23514');
SELECT pg_temp.expect_error('C33 CHECK: otp expired_at > created_at', $q$INSERT INTO otp_request (phone,otp_hash,purpose,expired_at,created_at) VALUES ('0900000001','h','LOGIN', now() - interval '1 hour', now())$q$, '23514');
SELECT pg_temp.expect_error('C34 CHECK: care_scenario followup_max >= followup_min', $q$INSERT INTO care_scenario (category,followup_min_days,followup_max_days,message_template) VALUES ('GENERAL',5,2,'x')$q$, '23514');

-- ---------------------------------------------------------------- D. Chống chồng lấn lịch (exclusion constraint)
-- Lịch #13: bác sĩ 5, ghế 2, 2026-11-10 09:00, dài 50 phút (kết thúc 09:50)
SELECT pg_temp.expect_error('D01 trùng bác sĩ (khác ghế)', $q$INSERT INTO appointment (patient_id,staff_id,chair_id,date_time,duration,status,source,created_by)
        VALUES (2,5,1,'2026-11-10 09:30+07',30,'CONFIRMED','PHONE',2)$q$, '23P01');
SELECT pg_temp.expect_error('D02 trùng ghế (khác bác sĩ)', $q$INSERT INTO appointment (patient_id,staff_id,chair_id,date_time,duration,status,source,created_by)
        VALUES (2,6,2,'2026-11-10 09:30+07',30,'CONFIRMED','PHONE',2)$q$, '23P01');
SELECT pg_temp.expect_ok   ('D03 liền kề (bắt đầu đúng lúc kết thúc) được phép', $q$INSERT INTO appointment (patient_id,staff_id,chair_id,date_time,duration,status,source,created_by)
        VALUES (2,5,2,'2026-11-10 09:50+07',30,'CONFIRMED','PHONE',2)$q$);
SELECT pg_temp.expect_ok   ('D04 lịch đã hủy không chiếm chỗ', $q$INSERT INTO appointment (patient_id,staff_id,chair_id,date_time,duration,status,source,created_by,cancelled_by_type,cancelled_by)
        VALUES (2,5,2,'2026-11-10 09:10+07',30,'CANCELLED','PHONE',2,'STAFF',2)$q$);
SELECT pg_temp.expect_error('D05 chuyển lịch hủy về CONFIRMED khi bị trùng thì bị chặn', $q$UPDATE appointment SET status = 'CONFIRMED', cancelled_by_type = NULL, cancelled_by = NULL
        WHERE date_time = '2026-11-10 09:10+07' AND staff_id = 5$q$, '23P01');

-- ---------------------------------------------------------------- E. Trigger và quy tắc nghiệp vụ ở DB
SELECT pg_temp.expect_error('E01 audit_log append-only (UPDATE)', $q$UPDATE audit_log SET action_type = 'X' WHERE log_id = 1$q$, 'P0001');
SELECT pg_temp.expect_error('E02 audit_log append-only (DELETE)', $q$DELETE FROM audit_log WHERE log_id = 1$q$, 'P0001');
SELECT pg_temp.expect_error('E03 notification_log append-only (UPDATE)', $q$UPDATE notification_log SET status = 'FAILED' WHERE notif_id = 1$q$, 'P0001');
SELECT pg_temp.expect_error('E04 notification_log append-only (DELETE)', $q$DELETE FROM notification_log WHERE notif_id = 1$q$, 'P0001');
SELECT pg_temp.expect_error('E05 cấm xóa feedback', $q$DELETE FROM feedback WHERE feedback_id = 1$q$, 'P0001');
SELECT pg_temp.expect_error('E06 cấm xóa complaint', $q$DELETE FROM complaint WHERE feedback_id = 2$q$, 'P0001');
SELECT pg_temp.expect_ok   ('E07 ẩn feedback hợp lệ', $q$UPDATE feedback SET is_hidden = TRUE, hidden_by = 1, hidden_at = now() WHERE feedback_id = 3$q$);
SELECT pg_temp.expect_error('E08 treatment_record bị khóa khi lịch COMPLETED (UPDATE)', $q$UPDATE treatment_record SET clinical_note = 'sửa' WHERE record_id = 1$q$, 'P0001');
SELECT pg_temp.expect_error('E09 treatment_record bị khóa khi lịch COMPLETED (DELETE)', $q$DELETE FROM treatment_record WHERE record_id = 1$q$, 'P0001');
SELECT pg_temp.expect_error('E10 no_show_event không được xóa', $q$DELETE FROM no_show_event WHERE event_id = 1$q$, 'P0001');
SELECT pg_temp.expect_error('E11 no_show_event không được sửa trường khác ngoài hoàn lại', $q$UPDATE no_show_event SET occurred_at = now() WHERE event_id = 1$q$, 'P0001');
SELECT pg_temp.expect_error('E12 no_show_event đã hoàn lại không được sửa tiếp', $q$UPDATE no_show_event SET reversed_reason = 'x' WHERE event_id = 4$q$, 'P0001');

-- Đánh giá <= 3 sao: alert_flag + tự tạo complaint
SELECT pg_temp.expect_true('E13 mẫu: feedback 2 sao đã tạo complaint tự động', EXISTS (SELECT 1 FROM complaint WHERE feedback_id = 2) AND (SELECT alert_flag FROM feedback WHERE feedback_id = 2));
SELECT pg_temp.expect_ok   ('E14 thêm feedback 3 sao', $q$INSERT INTO feedback (appointment_id,customer_id,patient_id,staff_id,rating_score,comment) VALUES (3,2,3,4,3,'Tạm được')$q$);
SELECT pg_temp.expect_true('E15 feedback 3 sao bật alert_flag', (SELECT alert_flag FROM feedback WHERE appointment_id = 3));
SELECT pg_temp.expect_true('E16 feedback 3 sao sinh complaint, chưa có người xử lý', EXISTS (SELECT 1 FROM complaint c JOIN feedback f USING (feedback_id) WHERE f.appointment_id = 3 AND c.handler_id IS NULL AND c.status = 'OPEN'));
SELECT pg_temp.expect_ok   ('E17 thêm feedback 5 sao', $q$INSERT INTO feedback (appointment_id,customer_id,patient_id,staff_id,rating_score) VALUES (5,3,5,4,5)$q$);
SELECT pg_temp.expect_true('E18 feedback 5 sao không cảnh báo, không sinh complaint', NOT (SELECT alert_flag FROM feedback WHERE appointment_id = 5)
        AND NOT EXISTS (SELECT 1 FROM complaint c JOIN feedback f USING (feedback_id) WHERE f.appointment_id = 5));

-- Blacklist tự động
SELECT pg_temp.expect_true('E19 mẫu: customer 3 (3 no-show chưa hoàn lại) bị Blacklist', (SELECT is_blacklisted FROM customer WHERE customer_id = 3));
SELECT pg_temp.expect_true('E20 mẫu: customer 5 (no-show đã hoàn lại) không bị Blacklist', NOT (SELECT is_blacklisted FROM customer WHERE customer_id = 5));
SELECT pg_temp.expect_ok   ('E21 hoàn lại 1 no-show (Is_Reversed)', $q$UPDATE no_show_event SET is_reversed = TRUE, reversed_by = 1, reversed_at = now(), reversed_reason = 'Quản lý gỡ khóa' WHERE event_id = 1$q$);
SELECT pg_temp.expect_true('E22 sau hoàn lại còn 2 sự kiện: tự gỡ Blacklist', NOT (SELECT is_blacklisted FROM customer WHERE customer_id = 3));
SELECT pg_temp.expect_error('E23 hoàn lại thiếu lý do bị chặn', $q$UPDATE no_show_event SET is_reversed = TRUE, reversed_by = 1, reversed_at = now() WHERE event_id = 2$q$, '23514');

-- Cảnh báo lịch bị ảnh hưởng bởi lịch nghỉ bác sĩ (view)
SELECT pg_temp.expect_true('E24 view cảnh báo: lịch #13 bị ảnh hưởng bởi lịch nghỉ #1', EXISTS (SELECT 1 FROM v_appointment_leave_conflict WHERE appointment_id = 13 AND leave_id = 1));
SELECT pg_temp.expect_true('E25 view cảnh báo: lịch không giao với lịch nghỉ không xuất hiện', NOT EXISTS (SELECT 1 FROM v_appointment_leave_conflict WHERE appointment_id = 14));
SELECT pg_temp.expect_true('E26 view cảnh báo: lịch nghỉ PENDING không sinh cảnh báo', NOT EXISTS (SELECT 1 FROM v_appointment_leave_conflict WHERE leave_id = 2));

-- ---------------------------------------------------------------- F. Toàn vẹn dữ liệu mẫu
SELECT pg_temp.expect_true('F01 mọi bảng đều có dữ liệu mẫu', (SELECT bool_and(n > 0) FROM (
   SELECT count(*) n FROM role_permission UNION ALL SELECT count(*) FROM staff_account UNION ALL SELECT count(*) FROM clinic_config
   UNION ALL SELECT count(*) FROM clinic_holiday UNION ALL SELECT count(*) FROM otp_request UNION ALL SELECT count(*) FROM customer
   UNION ALL SELECT count(*) FROM patient_profile UNION ALL SELECT count(*) FROM consent UNION ALL SELECT count(*) FROM opt_out
   UNION ALL SELECT count(*) FROM dental_chair UNION ALL SELECT count(*) FROM care_scenario UNION ALL SELECT count(*) FROM service
   UNION ALL SELECT count(*) FROM doctor_schedule UNION ALL SELECT count(*) FROM doctor_leave UNION ALL SELECT count(*) FROM appointment
   UNION ALL SELECT count(*) FROM appointment_service UNION ALL SELECT count(*) FROM treatment_record UNION ALL SELECT count(*) FROM treatment_progress
   UNION ALL SELECT count(*) FROM care_task UNION ALL SELECT count(*) FROM feedback UNION ALL SELECT count(*) FROM complaint
   UNION ALL SELECT count(*) FROM audit_log UNION ALL SELECT count(*) FROM notification_log UNION ALL SELECT count(*) FROM no_show_event) x));
SELECT pg_temp.expect_true('F02 đủ 6 trạng thái lịch hẹn trong dữ liệu mẫu', (SELECT count(DISTINCT status) FROM appointment) = 6);
SELECT pg_temp.expect_true('F03 sequence IDENTITY đã đồng bộ (không đụng ID mẫu)', (SELECT max(customer_id) FROM customer) >= 6);

-- ---------------------------------------------------------------- Kết quả
SELECT seq, test_name, CASE WHEN passed THEN 'PASS' ELSE 'FAIL' END AS result, detail FROM test_result ORDER BY seq;
SELECT count(*) FILTER (WHERE passed) AS passed, count(*) FILTER (WHERE NOT passed) AS failed, count(*) AS total FROM test_result;

DO $$
DECLARE v_fail INTEGER;
BEGIN
  SELECT count(*) INTO v_fail FROM test_result WHERE NOT passed;
  IF v_fail > 0 THEN RAISE EXCEPTION '% test thất bại — xem bảng kết quả ở trên', v_fail; END IF;
END $$;

ROLLBACK;
