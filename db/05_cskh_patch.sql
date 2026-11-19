-- =====================================================================
-- 05_cskh_patch.sql — Bổ sung schema cho phân hệ CSKH tự động hóa
-- Chạy SAU 01_schema.sql và 02_sample_data.sql (idempotent ở mức có thể).
-- Nguồn: SRS 2.2 (CSKH), 3.2 (UC-CSKH-01..10), 4.1 (mô hình dữ liệu),
--        mục "6 điểm mâu thuẫn đã chốt" (Care_Task 4 trạng thái, token K9 dùng một lần).
-- =====================================================================
BEGIN;

-- ---------------------------------------------------------------------
-- 1. care_task: thêm trạng thái "Cần hỗ trợ chuyên môn" (NEEDS_MEDICAL)
--    status VARCHAR(12) quá ngắn cho 'NEEDS_MEDICAL' (13 ký tự) -> nới rộng.
-- ---------------------------------------------------------------------
DROP INDEX IF EXISTS ix_task_open_sla;
DROP INDEX IF EXISTS ix_task_scheduled;

ALTER TABLE care_task ALTER COLUMN status TYPE VARCHAR(16);
ALTER TABLE care_task DROP CONSTRAINT IF EXISTS ck_task_status;
ALTER TABLE care_task ADD CONSTRAINT ck_task_status
  CHECK (status IN ('WAITING','IN_PROGRESS','NEEDS_MEDICAL','CLOSED'));

-- visible_from: thời điểm việc "đến hạn xuất hiện" trong danh sách lễ tân; mốc bắt đầu tính SLA 48h.
--   POST_VISIT_CHECK: = scheduled_at;  RECALL: = scheduled_at - recall_lead_days.
ALTER TABLE care_task ADD COLUMN IF NOT EXISTS visible_from TIMESTAMPTZ;
ALTER TABLE care_task ADD COLUMN IF NOT EXISTS medical_request_note TEXT;
ALTER TABLE care_task ADD COLUMN IF NOT EXISTS medical_response_note TEXT;
UPDATE care_task SET visible_from = scheduled_at WHERE visible_from IS NULL;
ALTER TABLE care_task ALTER COLUMN visible_from SET NOT NULL;

-- SRS 2.2.8: "Đóng bắt buộc có Result_Note"
ALTER TABLE care_task DROP CONSTRAINT IF EXISTS ck_task_closed_note;
ALTER TABLE care_task ADD CONSTRAINT ck_task_closed_note
  CHECK (status <> 'CLOSED' OR (result_note IS NOT NULL AND btrim(result_note) <> ''));

-- Chống sinh trùng việc tự động cho cùng một lịch hẹn (reconcile/event có thể chạy 2 lần)
CREATE UNIQUE INDEX IF NOT EXISTS ux_task_system_per_appt
  ON care_task (appointment_id, task_type)
  WHERE source = 'SYSTEM' AND appointment_id IS NOT NULL;

CREATE INDEX ix_task_open_sla ON care_task (sla_deadline) WHERE status <> 'CLOSED';
CREATE INDEX ix_task_scheduled ON care_task (scheduled_at) WHERE status <> 'CLOSED';
CREATE INDEX IF NOT EXISTS ix_task_visible ON care_task (visible_from) WHERE status <> 'CLOSED';

-- ---------------------------------------------------------------------
-- 2. notification_job: hàng đợi gửi tin (outbox)
--    notification_log là append-only (mỗi lần gửi/chuyển kênh = 1 dòng) nên không thể
--    dùng để lưu "việc gửi nào đã xử lý / bị bỏ qua". Bảng này lưu vòng đời của việc gửi,
--    chống gửi trùng (dedupe_key UNIQUE) và ghi nhận lý do KHÔNG gửi (opt-out, lịch đã hủy...).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_job (
  job_id            BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  dedupe_key        VARCHAR(120) NOT NULL,
  notif_type        VARCHAR(25)  NOT NULL,
  customer_id       BIGINT       NOT NULL,
  patient_id        BIGINT,
  appointment_id    BIGINT,
  care_task_id      BIGINT,
  target_time       TIMESTAMPTZ,
  reminder_hours    SMALLINT,
  scheduled_at      TIMESTAMPTZ  NOT NULL,
  next_attempt_at   TIMESTAMPTZ  NOT NULL,
  locked_until      TIMESTAMPTZ,
  status            VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
  attempts          SMALLINT     NOT NULL DEFAULT 0,
  skip_reason       VARCHAR(40),
  last_error        VARCHAR(255),
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  processed_at      TIMESTAMPTZ,
  CONSTRAINT pk_notification_job PRIMARY KEY (job_id),
  CONSTRAINT uq_job_dedupe UNIQUE (dedupe_key),
  CONSTRAINT fk_job_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
  CONSTRAINT fk_job_patient FOREIGN KEY (patient_id) REFERENCES patient_profile (patient_id),
  CONSTRAINT fk_job_appointment FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id),
  CONSTRAINT fk_job_task FOREIGN KEY (care_task_id) REFERENCES care_task (task_id),
  CONSTRAINT ck_job_type CHECK (notif_type IN ('OTP','APPOINTMENT_CONFIRMED','APPOINTMENT_RESCHEDULED','APPOINTMENT_CANCELLED','APPOINTMENT_REMINDER','POST_VISIT_CHECK','RECALL','BIRTHDAY')),
  CONSTRAINT ck_job_status CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','SKIPPED')),
  CONSTRAINT ck_job_skip CHECK ((status = 'SKIPPED') = (skip_reason IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_job_due ON notification_job (next_attempt_at) WHERE status IN ('PENDING','PROCESSING');
CREATE INDEX IF NOT EXISTS ix_job_customer ON notification_job (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_job_task ON notification_job (care_task_id);

-- Liên kết từng lần gửi (notification_log) với job. ADD COLUMN không kích hoạt trigger append-only.
ALTER TABLE notification_log ADD COLUMN IF NOT EXISTS job_id BIGINT;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_notification_log_job') THEN
    ALTER TABLE notification_log ADD CONSTRAINT fk_notification_log_job
      FOREIGN KEY (job_id) REFERENCES notification_job (job_id);
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_notif_job ON notification_log (job_id);

-- ---------------------------------------------------------------------
-- 3. feedback_token: link đánh giá K9 — token dùng một lần gắn Appointment_ID, không cần đăng nhập
--    Chỉ lưu SHA-256 của token (lộ DB không lộ link).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS feedback_token (
  token_id        BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  appointment_id  BIGINT      NOT NULL,
  token_hash      VARCHAR(64) NOT NULL,
  expires_at      TIMESTAMPTZ NOT NULL,
  used_at         TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_feedback_token PRIMARY KEY (token_id),
  CONSTRAINT uq_feedback_token_appt UNIQUE (appointment_id),
  CONSTRAINT uq_feedback_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_feedback_token_appt FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id)
);

-- ---------------------------------------------------------------------
-- 4. service: lưu ý đặc biệt trước khám (SRS 2.2.3 "Nội dung tự động ... lưu ý đặc biệt")
-- ---------------------------------------------------------------------
ALTER TABLE service ADD COLUMN IF NOT EXISTS pre_visit_instruction VARCHAR(255);
UPDATE service SET pre_visit_instruction = 'Vui lòng ăn no trước khi đến nhổ răng.'
 WHERE name = 'Nhổ răng khôn' AND pre_visit_instruction IS NULL;   -- ví dụ nêu trong SRS

-- SRS 2.2.2: "Nhóm phục hình/thẩm mỹ (Bọc răng sứ, Tẩy trắng...)" -> dữ liệu mẫu đang để Tẩy trắng chưa gán kịch bản
UPDATE service SET care_scenario_id = (SELECT scenario_id FROM care_scenario WHERE category = 'PROSTHETIC' LIMIT 1)
 WHERE name = 'Tẩy trắng răng' AND care_scenario_id IS NULL;

-- ---------------------------------------------------------------------
-- 5. clinic_config: tham số CSKH (không ghi đè giá trị đã có)
-- ---------------------------------------------------------------------
INSERT INTO clinic_config (config_key, config_value, config_group) VALUES
  ('clinic_name',              '"Phòng khám nha khoa CHIA"'::jsonb, 'care'),
  ('clinic_hotline',           '"1900 1234"'::jsonb,                'care'),   -- số hotline nêu ở SRS K9
  ('clinic_working_days',      '[1,2,3,4,5,6]'::jsonb,              'booking'), -- ISO: 1=Thứ 2 ... 7=CN (GIẢ ĐỊNH: T2–T7)
  ('reminder_grace_minutes',   '30'::jsonb,                         'notification'),
  ('notification_max_attempts','3'::jsonb,                          'notification'),
  ('feedback_link_ttl_days',   '14'::jsonb,                         'care'),
  ('recall_lead_days',         '7'::jsonb,                          'care'),
  ('alert_keywords',           '["đau kéo dài","đau nhức kéo dài","đau nhiều","chảy máu nhiều","máu chảy nhiều"]'::jsonb, 'care')
ON CONFLICT (config_key) DO NOTHING;

COMMIT;
