
BEGIN;

CREATE EXTENSION IF NOT EXISTS btree_gist;


CREATE OR REPLACE FUNCTION add_minutes(ts TIMESTAMPTZ, m INTEGER)
RETURNS TIMESTAMPTZ LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$ SELECT ts + (m * INTERVAL '1 minute') $$;



-- [Quản trị người dùng & Cấu hình]
CREATE TABLE role_permission (
  role_id                SMALLINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  role_name              VARCHAR(20) NOT NULL,
  permissions            JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT pk_role_permission PRIMARY KEY (role_id),
  CONSTRAINT uq_role_permission_role_name UNIQUE (role_name),
  CONSTRAINT ck_role_name CHECK (role_name IN ('MANAGER','RECEPTIONIST','DOCTOR'))
);
CREATE TABLE staff_account (
  staff_id               BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  username               VARCHAR(50) NOT NULL,
  email                  VARCHAR(254) NOT NULL,
  phone                  VARCHAR(15) NOT NULL,
  full_name              VARCHAR(100) NOT NULL,
  password_hash          VARCHAR(255) NOT NULL,
  status                 BOOLEAN NOT NULL DEFAULT TRUE,
  role_id                SMALLINT NOT NULL,
  CONSTRAINT pk_staff_account PRIMARY KEY (staff_id),
  CONSTRAINT uq_staff_account_username UNIQUE (username),
  CONSTRAINT uq_staff_account_email UNIQUE (email),
  CONSTRAINT uq_staff_account_phone UNIQUE (phone),
  CONSTRAINT fk_staff_account_role_id FOREIGN KEY (role_id) REFERENCES role_permission (role_id)
);
CREATE TABLE clinic_config (
  config_key             VARCHAR(100) NOT NULL,
  config_value           JSONB NOT NULL,
  config_group           VARCHAR(50) NOT NULL,
  updated_by             BIGINT,
  CONSTRAINT pk_clinic_config PRIMARY KEY (config_key),
  CONSTRAINT fk_clinic_config_updated_by FOREIGN KEY (updated_by) REFERENCES staff_account (staff_id)
);
CREATE TABLE clinic_holiday (
  holiday_id             BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  holiday_date           DATE NOT NULL,
  name                   VARCHAR(150) NOT NULL,
  CONSTRAINT pk_clinic_holiday PRIMARY KEY (holiday_id),
  CONSTRAINT uq_clinic_holiday_holiday_date UNIQUE (holiday_date)
);
CREATE TABLE otp_request (
  otp_id                 BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  phone                  VARCHAR(15) NOT NULL,
  otp_hash               VARCHAR(255) NOT NULL,
  purpose                VARCHAR(30) NOT NULL,
  expired_at             TIMESTAMPTZ NOT NULL,
  retry_count            SMALLINT NOT NULL DEFAULT 0,
  locked_until           TIMESTAMPTZ,
  is_used                BOOLEAN NOT NULL DEFAULT FALSE,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_otp_request PRIMARY KEY (otp_id),
  CONSTRAINT ck_otp_purpose CHECK (purpose IN ('LOGIN','BOOKING')),
  CONSTRAINT ck_otp_expiry CHECK (expired_at > created_at),
  CONSTRAINT ck_otp_retry CHECK (retry_count >= 0)
);





-- [Khách hàng & Hồ sơ y tế]

CREATE TABLE customer (
  customer_id            BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  phone                  VARCHAR(15) NOT NULL,
  is_blacklisted         BOOLEAN NOT NULL DEFAULT FALSE,
  first_consent_at       TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_customer PRIMARY KEY (customer_id),
  CONSTRAINT uq_customer_phone UNIQUE (phone),
  CONSTRAINT ck_customer_phone CHECK (phone ~ '^\+?[0-9]{9,15}$')
);
CREATE TABLE patient_profile (
  patient_id             BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  customer_id            BIGINT NOT NULL,
  full_name              VARCHAR(100) NOT NULL,
  birth_year             SMALLINT NOT NULL,
  dob                    DATE,
  gender                 VARCHAR(10),
  address                VARCHAR(255),
  medical_history        BYTEA,
  CONSTRAINT pk_patient_profile PRIMARY KEY (patient_id),
  CONSTRAINT fk_patient_profile_customer_id FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
  CONSTRAINT ck_patient_birth_year CHECK (birth_year BETWEEN 1900 AND 2100),
  CONSTRAINT ck_patient_dob_year CHECK (dob IS NULL OR EXTRACT(YEAR FROM dob) = birth_year),
  CONSTRAINT ck_patient_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER')),
  CONSTRAINT uq_patient_id_customer UNIQUE (patient_id, customer_id)
);
CREATE TABLE consent (
  consent_id             BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  phone                  VARCHAR(15) NOT NULL,
  customer_id            BIGINT,
  consent_time           TIMESTAMPTZ NOT NULL DEFAULT now(),
  channel                VARCHAR(30) NOT NULL,
  action                 VARCHAR(10) NOT NULL,
  policy_version         VARCHAR(20) NOT NULL,
  consented_by           VARCHAR(100),
  for_patient_id         BIGINT,
  relation               VARCHAR(30),
  is_guardian            BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_consent PRIMARY KEY (consent_id),
  CONSTRAINT fk_consent_customer_id FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
  CONSTRAINT fk_consent_for_patient_id FOREIGN KEY (for_patient_id) REFERENCES patient_profile (patient_id),
  CONSTRAINT ck_consent_action CHECK (action IN ('GIVEN','WITHDRAWN')),
  CONSTRAINT ck_consent_phone CHECK (phone ~ '^\+?[0-9]{9,15}$')
);
CREATE TABLE opt_out (
  opt_out_id             BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  customer_id            BIGINT NOT NULL,
  channel                VARCHAR(10) NOT NULL,
  excluded_notif_types   JSONB NOT NULL DEFAULT '[]'::jsonb,
  reason                 VARCHAR(255),
  is_active              BOOLEAN NOT NULL DEFAULT TRUE,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             BIGINT NOT NULL,
  CONSTRAINT pk_opt_out PRIMARY KEY (opt_out_id),
  CONSTRAINT fk_opt_out_customer_id FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
  CONSTRAINT fk_opt_out_created_by FOREIGN KEY (created_by) REFERENCES staff_account (staff_id),
  CONSTRAINT ck_optout_channel CHECK (channel IN ('ZALO_ZNS','SMS','ALL')),
  CONSTRAINT ck_optout_array CHECK (jsonb_typeof(excluded_notif_types) = 'array'),
  CONSTRAINT ck_optout_transactional CHECK (NOT (excluded_notif_types @> '["OTP"]'::jsonb OR excluded_notif_types @> '["APPOINTMENT_CONFIRMED"]'::jsonb OR excluded_notif_types @> '["APPOINTMENT_RESCHEDULED"]'::jsonb OR excluded_notif_types @> '["APPOINTMENT_CANCELLED"]'::jsonb))
);






-- [Đặt lịch & Tài nguyên]

CREATE TABLE dental_chair (
  chair_id               BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  chair_name             VARCHAR(50) NOT NULL,
  status                 VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT pk_dental_chair PRIMARY KEY (chair_id),
  CONSTRAINT uq_dental_chair_chair_name UNIQUE (chair_name),
  CONSTRAINT ck_chair_status CHECK (status IN ('ACTIVE','MAINTENANCE','INACTIVE'))
);
CREATE TABLE service (
  service_id             BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  name                   VARCHAR(150) NOT NULL,
  duration               SMALLINT NOT NULL,
  price                  NUMERIC(12,0),
  care_scenario_id       BIGINT,
  followup_cycle_days    INTEGER,
  is_active              BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_service PRIMARY KEY (service_id),
  CONSTRAINT uq_service_name UNIQUE (name),
  CONSTRAINT fk_service_care_scenario_id FOREIGN KEY (care_scenario_id) REFERENCES care_scenario (scenario_id),
  CONSTRAINT ck_service_duration CHECK (duration > 0),
  CONSTRAINT ck_service_price CHECK (price IS NULL OR price >= 0),
  CONSTRAINT ck_service_cycle CHECK (followup_cycle_days IS NULL OR followup_cycle_days > 0)
);
CREATE TABLE doctor_schedule (
  schedule_id            BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  staff_id               BIGINT NOT NULL,
  shift_date             DATE NOT NULL,
  start_time             TIME NOT NULL,
  end_time               TIME NOT NULL,
  status                 VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT pk_doctor_schedule PRIMARY KEY (schedule_id),
  CONSTRAINT fk_doctor_schedule_staff_id FOREIGN KEY (staff_id) REFERENCES staff_account (staff_id),
  CONSTRAINT ck_schedule_time CHECK (end_time > start_time),
  CONSTRAINT ck_schedule_status CHECK (status IN ('ACTIVE','CANCELLED')),
  CONSTRAINT uq_schedule_shift UNIQUE (staff_id, shift_date, start_time)
);
CREATE TABLE doctor_leave (
  leave_id               BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  staff_id               BIGINT NOT NULL,
  leave_date             DATE NOT NULL,
  start_time             TIME NOT NULL,
  end_time               TIME NOT NULL,
  reason                 VARCHAR(255) NOT NULL,
  status                 VARCHAR(10) NOT NULL DEFAULT 'PENDING',
  approved_by            BIGINT,
  approved_at            TIMESTAMPTZ,
  CONSTRAINT pk_doctor_leave PRIMARY KEY (leave_id),
  CONSTRAINT fk_doctor_leave_staff_id FOREIGN KEY (staff_id) REFERENCES staff_account (staff_id),
  CONSTRAINT fk_doctor_leave_approved_by FOREIGN KEY (approved_by) REFERENCES staff_account (staff_id),
  CONSTRAINT ck_leave_time CHECK (end_time > start_time),
  CONSTRAINT ck_leave_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  CONSTRAINT ck_leave_approved CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
CREATE TABLE appointment (
  appointment_id         BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  patient_id             BIGINT NOT NULL,
  staff_id               BIGINT NOT NULL,
  chair_id               BIGINT NOT NULL,
  date_time              TIMESTAMPTZ NOT NULL,
  duration               SMALLINT NOT NULL,
  end_time               TIMESTAMPTZ GENERATED ALWAYS AS (add_minutes(date_time, duration)) STORED,
  status                 VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  source                 VARCHAR(10) NOT NULL,
  booked_by_phone        VARCHAR(15),
  hold_until             TIMESTAMPTZ,
  cancelled_by           BIGINT,
  cancelled_by_type      VARCHAR(10),
  cancel_reason          VARCHAR(255),
  actual_arrival_at      TIMESTAMPTZ,
  is_priority            BOOLEAN NOT NULL DEFAULT FALSE,
  note                   VARCHAR(500),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             BIGINT,
  CONSTRAINT pk_appointment PRIMARY KEY (appointment_id),
  CONSTRAINT fk_appointment_patient_id FOREIGN KEY (patient_id) REFERENCES patient_profile (patient_id),
  CONSTRAINT fk_appointment_staff_id FOREIGN KEY (staff_id) REFERENCES staff_account (staff_id),
  CONSTRAINT fk_appointment_chair_id FOREIGN KEY (chair_id) REFERENCES dental_chair (chair_id),
  CONSTRAINT fk_appointment_booked_by_phone FOREIGN KEY (booked_by_phone) REFERENCES customer (phone),
  CONSTRAINT fk_appointment_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES staff_account (staff_id),
  CONSTRAINT fk_appointment_created_by FOREIGN KEY (created_by) REFERENCES staff_account (staff_id),
  CONSTRAINT ck_appt_status CHECK (status IN ('PENDING','CONFIRMED','CHECKED_IN','COMPLETED','CANCELLED','NO_SHOW')),
  CONSTRAINT ck_appt_source CHECK (source IN ('ONLINE','PHONE','SOCIAL','WALK_IN')),
  CONSTRAINT ck_appt_duration CHECK (duration > 0),
  CONSTRAINT ck_appt_hold_pending CHECK (status <> 'PENDING' OR hold_until IS NOT NULL),
  CONSTRAINT ck_appt_hold_before_start CHECK (hold_until IS NULL OR hold_until <= date_time),
  CONSTRAINT ck_appt_cancel_type CHECK (cancelled_by_type IS NULL OR cancelled_by_type IN ('CUSTOMER','STAFF','SYSTEM')),
  CONSTRAINT ck_appt_cancel_required CHECK (status <> 'CANCELLED' OR cancelled_by_type IS NOT NULL),
  CONSTRAINT ck_appt_cancel_staff CHECK (COALESCE(cancelled_by_type = 'STAFF', FALSE) = (cancelled_by IS NOT NULL)),
  CONSTRAINT ck_appt_arrival CHECK (status NOT IN ('CHECKED_IN','COMPLETED') OR actual_arrival_at IS NOT NULL),
  CONSTRAINT ck_appt_creator CHECK (source = 'ONLINE' OR created_by IS NOT NULL),
  CONSTRAINT uq_appt_patient UNIQUE (appointment_id, patient_id),
  CONSTRAINT uq_appt_patient_staff UNIQUE (appointment_id, patient_id, staff_id),
  CONSTRAINT ex_appt_staff_no_overlap EXCLUDE USING gist (staff_id WITH =, tstzrange(date_time, end_time) WITH &&) WHERE (status IN ('PENDING','CONFIRMED','CHECKED_IN')),
  CONSTRAINT ex_appt_chair_no_overlap EXCLUDE USING gist (chair_id WITH =, tstzrange(date_time, end_time) WITH &&) WHERE (status IN ('PENDING','CONFIRMED','CHECKED_IN'))
);
CREATE TABLE appointment_service (
  appt_service_id        BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  appointment_id         BIGINT NOT NULL,
  service_id             BIGINT,
  is_temporary           BOOLEAN NOT NULL DEFAULT FALSE,
  duration_snapshot      SMALLINT NOT NULL,
  price_snapshot         NUMERIC(12,0),
  is_performed           BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_appointment_service PRIMARY KEY (appt_service_id),
  CONSTRAINT fk_appointment_service_appointment_id FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id),
  CONSTRAINT fk_appointment_service_service_id FOREIGN KEY (service_id) REFERENCES service (service_id),
  CONSTRAINT ck_apsvc_service CHECK (is_temporary OR service_id IS NOT NULL),
  CONSTRAINT ck_apsvc_duration CHECK (duration_snapshot > 0),
  CONSTRAINT ck_apsvc_price CHECK (price_snapshot IS NULL OR price_snapshot >= 0)
);





-- [Chăm sóc khách hàng & Phản hồi] 
CREATE TABLE care_scenario (
  scenario_id            BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  category               VARCHAR(15) NOT NULL,
  followup_min_days      SMALLINT NOT NULL,
  followup_max_days      SMALLINT NOT NULL,
  message_template       TEXT NOT NULL,
  CONSTRAINT pk_care_scenario PRIMARY KEY (scenario_id),
  CONSTRAINT ck_scenario_category CHECK (category IN ('GENERAL','MINOR_SURGERY','PROSTHETIC','LONG_TERM')),
  CONSTRAINT ck_scenario_days CHECK (followup_min_days >= 0 AND followup_max_days >= followup_min_days)
);
CREATE TABLE care_task (
  task_id                BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  appointment_id         BIGINT,
  patient_id             BIGINT NOT NULL,
  task_type              VARCHAR(15) NOT NULL,
  source                 VARCHAR(10) NOT NULL,
  care_scenario_id       BIGINT,
  assignee_id            BIGINT NOT NULL,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  scheduled_at           TIMESTAMPTZ NOT NULL,
  sla_deadline           TIMESTAMPTZ NOT NULL,
  status                 VARCHAR(12) NOT NULL DEFAULT 'WAITING',
  is_overdue             BOOLEAN NOT NULL DEFAULT FALSE,
  result_note            TEXT,
  completed_at           TIMESTAMPTZ,
  CONSTRAINT pk_care_task PRIMARY KEY (task_id),
  CONSTRAINT fk_care_task_appointment_id FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id),
  CONSTRAINT fk_care_task_patient_id FOREIGN KEY (patient_id) REFERENCES patient_profile (patient_id),
  CONSTRAINT fk_care_task_care_scenario_id FOREIGN KEY (care_scenario_id) REFERENCES care_scenario (scenario_id),
  CONSTRAINT fk_care_task_assignee_id FOREIGN KEY (assignee_id) REFERENCES staff_account (staff_id),
  CONSTRAINT ck_task_type CHECK (task_type IN ('POST_VISIT_CHECK','RECALL','CONFIRM_CALL')),
  CONSTRAINT ck_task_source CHECK (source IN ('SYSTEM','MANUAL')),
  CONSTRAINT ck_task_status CHECK (status IN ('WAITING','IN_PROGRESS','CLOSED')),
  CONSTRAINT ck_task_sla CHECK (sla_deadline >= created_at),
  CONSTRAINT ck_task_closed CHECK (status <> 'CLOSED' OR completed_at IS NOT NULL)
);
CREATE TABLE feedback (
  feedback_id            BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  appointment_id         BIGINT NOT NULL,
  customer_id            BIGINT NOT NULL,
  patient_id             BIGINT NOT NULL,
  staff_id               BIGINT NOT NULL,
  rating_score           SMALLINT NOT NULL,
  comment                TEXT,
  alert_flag             BOOLEAN NOT NULL DEFAULT FALSE,
  is_hidden              BOOLEAN NOT NULL DEFAULT FALSE,
  hidden_by              BIGINT,
  hidden_at              TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_feedback PRIMARY KEY (feedback_id),
  CONSTRAINT uq_feedback_appointment_id UNIQUE (appointment_id),
  CONSTRAINT fk_feedback_appointment_id FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id),
  CONSTRAINT fk_feedback_customer_id FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
  CONSTRAINT fk_feedback_patient_id FOREIGN KEY (patient_id) REFERENCES patient_profile (patient_id),
  CONSTRAINT fk_feedback_staff_id FOREIGN KEY (staff_id) REFERENCES staff_account (staff_id),
  CONSTRAINT fk_feedback_hidden_by FOREIGN KEY (hidden_by) REFERENCES staff_account (staff_id),
  CONSTRAINT ck_feedback_rating CHECK (rating_score BETWEEN 1 AND 5),
  CONSTRAINT ck_feedback_hidden CHECK (NOT is_hidden OR (hidden_by IS NOT NULL AND hidden_at IS NOT NULL)),
  CONSTRAINT fk_feedback_appt FOREIGN KEY (appointment_id, patient_id, staff_id) REFERENCES appointment (appointment_id, patient_id, staff_id),
  CONSTRAINT fk_feedback_patient_customer FOREIGN KEY (patient_id, customer_id) REFERENCES patient_profile (patient_id, customer_id)
);
CREATE TABLE complaint (
  complaint_id           BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  patient_id             BIGINT NOT NULL,
  feedback_id            BIGINT,
  content                TEXT NOT NULL,
  severity               VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
  handler_id             BIGINT,
  action_taken           TEXT,
  result                 TEXT,
  status                 VARCHAR(18) NOT NULL DEFAULT 'OPEN',
  sla_deadline           TIMESTAMPTZ,
  is_hidden              BOOLEAN NOT NULL DEFAULT FALSE,
  hidden_by              BIGINT,
  hidden_at              TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at              TIMESTAMPTZ,
  CONSTRAINT pk_complaint PRIMARY KEY (complaint_id),
  CONSTRAINT fk_complaint_patient_id FOREIGN KEY (patient_id) REFERENCES patient_profile (patient_id),
  CONSTRAINT fk_complaint_feedback_id FOREIGN KEY (feedback_id) REFERENCES feedback (feedback_id),
  CONSTRAINT fk_complaint_handler_id FOREIGN KEY (handler_id) REFERENCES staff_account (staff_id),
  CONSTRAINT fk_complaint_hidden_by FOREIGN KEY (hidden_by) REFERENCES staff_account (staff_id),
  CONSTRAINT ck_complaint_severity CHECK (severity IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT ck_complaint_status CHECK (status IN ('OPEN','IN_PROGRESS','WAITING_RESPONSE','CLOSED')),
  CONSTRAINT ck_complaint_closed CHECK (status <> 'CLOSED' OR (closed_at IS NOT NULL AND result IS NOT NULL)),
  CONSTRAINT ck_complaint_hidden CHECK (NOT is_hidden OR (hidden_by IS NOT NULL AND hidden_at IS NOT NULL))
);






-- [Tiến trình điều trị]
CREATE TABLE treatment_record (
  record_id              BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  appointment_id         BIGINT NOT NULL,
  patient_id             BIGINT NOT NULL,
  staff_id               BIGINT NOT NULL,
  clinical_note          TEXT,
  treatment_result       TEXT,
  next_appointment_date  DATE,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_treatment_record PRIMARY KEY (record_id),
  CONSTRAINT uq_treatment_record_appointment_id UNIQUE (appointment_id),
  CONSTRAINT fk_treatment_record_appointment_id FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id),
  CONSTRAINT fk_treatment_record_patient_id FOREIGN KEY (patient_id) REFERENCES patient_profile (patient_id),
  CONSTRAINT fk_treatment_record_staff_id FOREIGN KEY (staff_id) REFERENCES staff_account (staff_id),
  CONSTRAINT fk_record_appt_patient FOREIGN KEY (appointment_id, patient_id) REFERENCES appointment (appointment_id, patient_id)
);
CREATE TABLE treatment_progress (
  progress_id            BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  patient_id             BIGINT NOT NULL,
  service_id             BIGINT NOT NULL,
  start_date             DATE NOT NULL,
  expected_end           DATE,
  current_session        SMALLINT NOT NULL DEFAULT 0,
  total_sessions         SMALLINT NOT NULL,
  status                 VARCHAR(12) NOT NULL DEFAULT 'IN_PROGRESS',
  CONSTRAINT pk_treatment_progress PRIMARY KEY (progress_id),
  CONSTRAINT fk_treatment_progress_patient_id FOREIGN KEY (patient_id) REFERENCES patient_profile (patient_id),
  CONSTRAINT fk_treatment_progress_service_id FOREIGN KEY (service_id) REFERENCES service (service_id),
  CONSTRAINT ck_progress_sessions CHECK (total_sessions > 0 AND current_session >= 0 AND current_session <= total_sessions),
  CONSTRAINT ck_progress_dates CHECK (expected_end IS NULL OR expected_end >= start_date),
  CONSTRAINT ck_progress_status CHECK (status IN ('IN_PROGRESS','PAUSED','COMPLETED','CANCELLED'))
);






-- [Log & Tracking]
CREATE TABLE audit_log (
  log_id                 BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  actor_type             VARCHAR(10) NOT NULL,
  actor_staff_id         BIGINT,
  actor_customer_id      BIGINT,
  action_type            VARCHAR(50) NOT NULL,
  target_entity          VARCHAR(64) NOT NULL,
  target_id              VARCHAR(64) NOT NULL,
  old_value              JSONB,
  new_value              JSONB,
  ip_address             INET,
  event_time             TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_audit_log PRIMARY KEY (log_id),
  CONSTRAINT fk_audit_log_actor_staff_id FOREIGN KEY (actor_staff_id) REFERENCES staff_account (staff_id),
  CONSTRAINT fk_audit_log_actor_customer_id FOREIGN KEY (actor_customer_id) REFERENCES customer (customer_id),
  CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('STAFF','CUSTOMER','SYSTEM')),
  CONSTRAINT ck_audit_actor CHECK ((actor_type = 'STAFF' AND actor_staff_id IS NOT NULL AND actor_customer_id IS NULL) OR (actor_type = 'CUSTOMER' AND actor_customer_id IS NOT NULL AND actor_staff_id IS NULL) OR (actor_type = 'SYSTEM' AND actor_staff_id IS NULL AND actor_customer_id IS NULL))
);
CREATE TABLE notification_log (
  notif_id               BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  customer_id            BIGINT,
  appointment_id         BIGINT,
  care_task_id           BIGINT,
  notif_type             VARCHAR(25) NOT NULL,
  channel                VARCHAR(10) NOT NULL,
  recipient_phone        VARCHAR(15) NOT NULL,
  content                TEXT NOT NULL,
  status                 VARCHAR(10) NOT NULL,
  error_code             VARCHAR(50),
  provider_msg_id        VARCHAR(100),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at                TIMESTAMPTZ,
  CONSTRAINT pk_notification_log PRIMARY KEY (notif_id),
  CONSTRAINT fk_notification_log_customer_id FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
  CONSTRAINT fk_notification_log_appointment_id FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id),
  CONSTRAINT fk_notification_log_care_task_id FOREIGN KEY (care_task_id) REFERENCES care_task (task_id),
  CONSTRAINT ck_notif_type CHECK (notif_type IN ('OTP','APPOINTMENT_CONFIRMED','APPOINTMENT_RESCHEDULED','APPOINTMENT_CANCELLED','APPOINTMENT_REMINDER','POST_VISIT_CHECK','RECALL','BIRTHDAY')),
  CONSTRAINT ck_notif_channel CHECK (channel IN ('ZALO_ZNS','SMS')),
  CONSTRAINT ck_notif_status CHECK (status IN ('SUCCESS','FAILED')),
  CONSTRAINT ck_notif_customer CHECK (customer_id IS NOT NULL OR notif_type = 'OTP'),
  CONSTRAINT ck_notif_error CHECK ((status = 'FAILED') = (error_code IS NOT NULL)),
  CONSTRAINT ck_notif_sent CHECK (status <> 'SUCCESS' OR sent_at IS NOT NULL)
);
CREATE TABLE no_show_event (
  event_id               BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
  patient_id             BIGINT NOT NULL,
  appointment_id         BIGINT NOT NULL,
  occurred_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  is_reversed            BOOLEAN NOT NULL DEFAULT FALSE,
  reversed_by            BIGINT,
  reversed_at            TIMESTAMPTZ,
  reversed_reason        VARCHAR(255),
  CONSTRAINT pk_no_show_event PRIMARY KEY (event_id),
  CONSTRAINT uq_no_show_event_appointment_id UNIQUE (appointment_id),
  CONSTRAINT fk_no_show_event_patient_id FOREIGN KEY (patient_id) REFERENCES patient_profile (patient_id),
  CONSTRAINT fk_no_show_event_appointment_id FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id),
  CONSTRAINT fk_no_show_event_reversed_by FOREIGN KEY (reversed_by) REFERENCES staff_account (staff_id),
  CONSTRAINT ck_noshow_reversed CHECK (NOT is_reversed OR (reversed_by IS NOT NULL AND reversed_at IS NOT NULL AND reversed_reason IS NOT NULL)),
  CONSTRAINT fk_noshow_appt_patient FOREIGN KEY (appointment_id, patient_id) REFERENCES appointment (appointment_id, patient_id)
);




-- Chỉ mục
CREATE INDEX ix_otp_phone_created ON otp_request (phone, created_at DESC);
CREATE INDEX ix_patient_customer ON patient_profile (customer_id);
CREATE INDEX ix_patient_name ON patient_profile (lower(full_name));
CREATE INDEX ix_consent_phone ON consent (phone, consent_time DESC);
CREATE INDEX ix_consent_customer ON consent (customer_id);
CREATE INDEX ix_optout_customer_active ON opt_out (customer_id) WHERE is_active;
CREATE INDEX ix_service_scenario ON service (care_scenario_id);
CREATE INDEX ix_leave_staff_date ON doctor_leave (staff_id, leave_date);
CREATE INDEX ix_appt_patient ON appointment (patient_id, date_time DESC);
CREATE INDEX ix_appt_staff_time ON appointment (staff_id, date_time);
CREATE INDEX ix_appt_status_time ON appointment (status, date_time);
CREATE INDEX ix_appt_hold ON appointment (hold_until) WHERE status = 'PENDING';
CREATE INDEX ix_appt_phone ON appointment (booked_by_phone);
CREATE UNIQUE INDEX ux_apsvc_appt_service ON appointment_service (appointment_id, service_id) WHERE service_id IS NOT NULL;
CREATE INDEX ix_apsvc_service ON appointment_service (service_id);
CREATE INDEX ix_record_patient ON treatment_record (patient_id, created_at DESC);
CREATE INDEX ix_progress_patient ON treatment_progress (patient_id, status);
CREATE INDEX ix_task_open_sla ON care_task (sla_deadline) WHERE status <> 'CLOSED';
CREATE INDEX ix_task_assignee ON care_task (assignee_id, status);
CREATE INDEX ix_task_patient ON care_task (patient_id);
CREATE INDEX ix_task_appt ON care_task (appointment_id);
CREATE INDEX ix_task_scheduled ON care_task (scheduled_at) WHERE status <> 'CLOSED';
CREATE INDEX ix_feedback_staff ON feedback (staff_id, created_at DESC);
CREATE INDEX ix_feedback_alert ON feedback (created_at DESC) WHERE alert_flag;
CREATE UNIQUE INDEX ux_complaint_feedback ON complaint (feedback_id) WHERE feedback_id IS NOT NULL;
CREATE INDEX ix_complaint_status ON complaint (status, created_at DESC);
CREATE INDEX ix_complaint_handler ON complaint (handler_id);
CREATE INDEX ix_complaint_patient ON complaint (patient_id);
CREATE INDEX ix_audit_target ON audit_log (target_entity, target_id);
CREATE INDEX ix_audit_time ON audit_log (event_time DESC);
CREATE INDEX ix_audit_staff ON audit_log (actor_staff_id);
CREATE INDEX ix_audit_customer ON audit_log (actor_customer_id);
CREATE INDEX ix_notif_customer ON notification_log (customer_id, created_at DESC);
CREATE INDEX ix_notif_appt ON notification_log (appointment_id);
CREATE INDEX ix_notif_task ON notification_log (care_task_id);
CREATE INDEX ix_notif_status_time ON notification_log (status, created_at DESC);
CREATE INDEX ix_noshow_patient ON no_show_event (patient_id, occurred_at DESC) WHERE NOT is_reversed;







-- Trigger

--append-only tuyệt đối
CREATE OR REPLACE FUNCTION fn_block_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION '% là bảng append-only, không được % bản ghi', TG_TABLE_NAME, TG_OP;
END $$;

CREATE TRIGGER trg_audit_log_immutable BEFORE UPDATE OR DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION fn_block_mutation();
CREATE TRIGGER trg_notification_log_immutable BEFORE UPDATE OR DELETE ON notification_log
  FOR EACH ROW EXECUTE FUNCTION fn_block_mutation();

-- Cấm xóa vật lý phản hồi/khiếu nại
CREATE OR REPLACE FUNCTION fn_block_delete() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Không được xóa vật lý bản ghi của %, hãy dùng is_hidden', TG_TABLE_NAME;
END $$;

CREATE TRIGGER trg_feedback_nodelete BEFORE DELETE ON feedback
  FOR EACH ROW EXECUTE FUNCTION fn_block_delete();
CREATE TRIGGER trg_complaint_nodelete BEFORE DELETE ON complaint
  FOR EACH ROW EXECUTE FUNCTION fn_block_delete();

--no_show_event: append-only, chỉ cho phép hoàn lại một chiều 
CREATE OR REPLACE FUNCTION fn_no_show_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'no_show_event là append-only, không được xóa';
  END IF;
  IF OLD.is_reversed THEN
    RAISE EXCEPTION 'no_show_event đã hoàn lại, không được sửa';
  END IF;
  IF NOT NEW.is_reversed
     OR (NEW.event_id, NEW.patient_id, NEW.appointment_id, NEW.occurred_at)
        IS DISTINCT FROM (OLD.event_id, OLD.patient_id, OLD.appointment_id, OLD.occurred_at) THEN
    RAISE EXCEPTION 'no_show_event chỉ cho phép hoàn lại (is_reversed = TRUE) cùng reversed_*';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_no_show_guard BEFORE UPDATE OR DELETE ON no_show_event
  FOR EACH ROW EXECUTE FUNCTION fn_no_show_guard();

--Blacklist tự động 
CREATE OR REPLACE FUNCTION fn_refresh_blacklist(p_customer_id BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
  v_threshold INTEGER;
  v_months    INTEGER;
  v_cnt       INTEGER;
BEGIN
  SELECT COALESCE((SELECT (config_value #>> '{}')::INTEGER FROM clinic_config
                    WHERE config_key = 'blacklist_threshold'), 3) INTO v_threshold;
  SELECT COALESCE((SELECT (config_value #>> '{}')::INTEGER FROM clinic_config
                    WHERE config_key = 'blacklist_window_months'), 12) INTO v_months;
  SELECT count(*) INTO v_cnt
    FROM no_show_event e
    JOIN patient_profile p ON p.patient_id = e.patient_id
   WHERE p.customer_id = p_customer_id
     AND NOT e.is_reversed
     AND e.occurred_at >= now() - make_interval(months => v_months);
  UPDATE customer SET is_blacklisted = (v_cnt >= v_threshold)
   WHERE customer_id = p_customer_id AND is_blacklisted IS DISTINCT FROM (v_cnt >= v_threshold);
END $$;

CREATE OR REPLACE FUNCTION fn_no_show_after_change() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE v_customer BIGINT;
BEGIN
  SELECT customer_id INTO v_customer FROM patient_profile WHERE patient_id = NEW.patient_id;
  PERFORM fn_refresh_blacklist(v_customer);
  RETURN NEW;
END $$;

CREATE TRIGGER trg_no_show_blacklist AFTER INSERT OR UPDATE ON no_show_event
  FOR EACH ROW EXECUTE FUNCTION fn_no_show_after_change();

-----------------Treatment_Record chỉ sửa/xóa được khi lịch hẹn chưa COMPLETED 
CREATE OR REPLACE FUNCTION fn_treatment_record_lock() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE v_status VARCHAR;
BEGIN
  SELECT status INTO v_status FROM appointment WHERE appointment_id = OLD.appointment_id;
  IF v_status = 'COMPLETED' THEN
    RAISE EXCEPTION 'treatment_record bị khóa vì lịch hẹn % đã COMPLETED', OLD.appointment_id;
  END IF;
  IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_treatment_record_lock BEFORE UPDATE OR DELETE ON treatment_record
  FOR EACH ROW EXECUTE FUNCTION fn_treatment_record_lock();

------------------Đánh giá <= 3 sao: bật alert_flag và tự sinh Complaint (matrix 4.1, FR-31)
CREATE OR REPLACE FUNCTION fn_feedback_before_insert() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.rating_score <= 3 THEN NEW.alert_flag := TRUE; END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_feedback_bi BEFORE INSERT ON feedback
  FOR EACH ROW EXECUTE FUNCTION fn_feedback_before_insert();

CREATE OR REPLACE FUNCTION fn_feedback_after_insert() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.rating_score <= 3 THEN
    INSERT INTO complaint (patient_id, feedback_id, content)
    VALUES (NEW.patient_id, NEW.feedback_id,
            COALESCE(NULLIF(btrim(NEW.comment), ''), 'Đánh giá ' || NEW.rating_score || ' sao'));
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_feedback_ai AFTER INSERT ON feedback
  FOR EACH ROW EXECUTE FUNCTION fn_feedback_after_insert();





-- View: lịch CONFIRMED bị ảnh hưởng bởi lịch nghỉ APPROVED của bác sĩ (FR-13, UC-10)
-- Tính động nên tự hết cảnh báo khi lịch được dời/hủy; không cần cột cờ trên appointment.
CREATE OR REPLACE VIEW v_appointment_leave_conflict AS
SELECT a.appointment_id, a.patient_id, a.staff_id, a.date_time, a.end_time, l.leave_id
  FROM appointment a
  JOIN doctor_leave l
    ON l.staff_id = a.staff_id
   AND l.status = 'APPROVED'
   AND tstzrange(a.date_time, a.end_time) &&
       tstzrange((l.leave_date + l.start_time) AT TIME ZONE 'Asia/Ho_Chi_Minh',
                 (l.leave_date + l.end_time)   AT TIME ZONE 'Asia/Ho_Chi_Minh')
 WHERE a.status = 'CONFIRMED';



INSERT INTO role_permission (role_name) VALUES ('MANAGER'), ('RECEPTIONIST'), ('DOCTOR');

INSERT INTO clinic_config (config_key, config_value, config_group) VALUES
  ('booking_min_lead_hours',       '2'::jsonb,       'booking'),      -- 2.1 mục 9: tự đặt trước tối thiểu 2 giờ
  ('booking_max_lead_days',        '30'::jsonb,      'booking'),      -- tối đa 30 ngày
  ('customer_change_min_hours',    '4'::jsonb,       'booking'),      -- tự đổi/hủy trước tối thiểu 4 giờ
  ('late_noshow_minutes',          '15'::jsonb,      'booking'),      -- đến muộn quá 15 phút
  ('new_patient_buffer_minutes',   '15'::jsonb,      'booking'),      -- đệm hồ sơ mới 15 phút
  ('blacklist_threshold',          '3'::jsonb,       'blacklist'),    -- từ 3 lần Không đến
  ('reminder_hours_before',        '[24, 2]'::jsonb, 'notification'), -- nhắc trước 24h và 2h
  ('care_sla_hours',               '48'::jsonb,      'care');         -- SLA 48 giờ


COMMIT;
