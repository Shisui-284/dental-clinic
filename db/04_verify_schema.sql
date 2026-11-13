-- =====================================================================
-- 04_verify_schema.sql — Đối chiếu DATABASE THỰC TẾ với Schema/Data Dictionary (Task 6.1)
-- Chạy sau 01_schema.sql (và 02_sample_data.sql để kiểm tra mục 7–8). Chỉ đọc (SELECT), không sửa dữ liệu.
-- Mỗi mục in ra các dòng LỆCH; mục không có dòng nào = khớp. Mục cuối là bảng tổng hợp PASS/FAIL.
-- Danh sách kỳ vọng được sinh tự động từ cùng spec với 01_schema.sql và data_dictionary.xlsx.
-- =====================================================================
\set ON_ERROR_STOP on

CREATE TEMP TABLE exp_column (tbl text, col text, data_type text, char_len int, is_nullable text);
INSERT INTO exp_column VALUES
  ('role_permission','role_id','smallint',NULL,'NO'),
  ('role_permission','role_name','character varying',20,'NO'),
  ('role_permission','permissions','jsonb',NULL,'NO'),
  ('staff_account','staff_id','bigint',NULL,'NO'),
  ('staff_account','username','character varying',50,'NO'),
  ('staff_account','email','character varying',254,'NO'),
  ('staff_account','phone','character varying',15,'NO'),
  ('staff_account','full_name','character varying',100,'NO'),
  ('staff_account','password_hash','character varying',255,'NO'),
  ('staff_account','status','boolean',NULL,'NO'),
  ('staff_account','role_id','smallint',NULL,'NO'),
  ('clinic_config','config_key','character varying',100,'NO'),
  ('clinic_config','config_value','jsonb',NULL,'NO'),
  ('clinic_config','config_group','character varying',50,'NO'),
  ('clinic_config','updated_by','bigint',NULL,'YES'),
  ('clinic_holiday','holiday_id','bigint',NULL,'NO'),
  ('clinic_holiday','holiday_date','date',NULL,'NO'),
  ('clinic_holiday','name','character varying',150,'NO'),
  ('otp_request','otp_id','bigint',NULL,'NO'),
  ('otp_request','phone','character varying',15,'NO'),
  ('otp_request','otp_hash','character varying',255,'NO'),
  ('otp_request','purpose','character varying',30,'NO'),
  ('otp_request','expired_at','timestamp with time zone',NULL,'NO'),
  ('otp_request','retry_count','smallint',NULL,'NO'),
  ('otp_request','locked_until','timestamp with time zone',NULL,'YES'),
  ('otp_request','is_used','boolean',NULL,'NO'),
  ('otp_request','created_at','timestamp with time zone',NULL,'NO'),
  ('customer','customer_id','bigint',NULL,'NO'),
  ('customer','phone','character varying',15,'NO'),
  ('customer','is_blacklisted','boolean',NULL,'NO'),
  ('customer','first_consent_at','timestamp with time zone',NULL,'YES'),
  ('customer','created_at','timestamp with time zone',NULL,'NO'),
  ('patient_profile','patient_id','bigint',NULL,'NO'),
  ('patient_profile','customer_id','bigint',NULL,'NO'),
  ('patient_profile','full_name','character varying',100,'NO'),
  ('patient_profile','birth_year','smallint',NULL,'NO'),
  ('patient_profile','dob','date',NULL,'YES'),
  ('patient_profile','gender','character varying',10,'YES'),
  ('patient_profile','address','character varying',255,'YES'),
  ('patient_profile','medical_history','bytea',NULL,'YES'),
  ('consent','consent_id','bigint',NULL,'NO'),
  ('consent','phone','character varying',15,'NO'),
  ('consent','customer_id','bigint',NULL,'YES'),
  ('consent','consent_time','timestamp with time zone',NULL,'NO'),
  ('consent','channel','character varying',30,'NO'),
  ('consent','action','character varying',10,'NO'),
  ('consent','policy_version','character varying',20,'NO'),
  ('consent','consented_by','character varying',100,'YES'),
  ('consent','for_patient_id','bigint',NULL,'YES'),
  ('consent','relation','character varying',30,'YES'),
  ('consent','is_guardian','boolean',NULL,'NO'),
  ('opt_out','opt_out_id','bigint',NULL,'NO'),
  ('opt_out','customer_id','bigint',NULL,'NO'),
  ('opt_out','channel','character varying',10,'NO'),
  ('opt_out','excluded_notif_types','jsonb',NULL,'NO'),
  ('opt_out','reason','character varying',255,'YES'),
  ('opt_out','is_active','boolean',NULL,'NO'),
  ('opt_out','created_at','timestamp with time zone',NULL,'NO'),
  ('opt_out','created_by','bigint',NULL,'NO'),
  ('dental_chair','chair_id','bigint',NULL,'NO'),
  ('dental_chair','chair_name','character varying',50,'NO'),
  ('dental_chair','status','character varying',15,'NO'),
  ('care_scenario','scenario_id','bigint',NULL,'NO'),
  ('care_scenario','category','character varying',15,'NO'),
  ('care_scenario','followup_min_days','smallint',NULL,'NO'),
  ('care_scenario','followup_max_days','smallint',NULL,'NO'),
  ('care_scenario','message_template','text',NULL,'NO'),
  ('service','service_id','bigint',NULL,'NO'),
  ('service','name','character varying',150,'NO'),
  ('service','duration','smallint',NULL,'NO'),
  ('service','price','numeric',NULL,'YES'),
  ('service','care_scenario_id','bigint',NULL,'YES'),
  ('service','followup_cycle_days','integer',NULL,'YES'),
  ('service','is_active','boolean',NULL,'NO'),
  ('doctor_schedule','schedule_id','bigint',NULL,'NO'),
  ('doctor_schedule','staff_id','bigint',NULL,'NO'),
  ('doctor_schedule','shift_date','date',NULL,'NO'),
  ('doctor_schedule','start_time','time without time zone',NULL,'NO'),
  ('doctor_schedule','end_time','time without time zone',NULL,'NO'),
  ('doctor_schedule','status','character varying',10,'NO'),
  ('doctor_leave','leave_id','bigint',NULL,'NO'),
  ('doctor_leave','staff_id','bigint',NULL,'NO'),
  ('doctor_leave','leave_date','date',NULL,'NO'),
  ('doctor_leave','start_time','time without time zone',NULL,'NO'),
  ('doctor_leave','end_time','time without time zone',NULL,'NO'),
  ('doctor_leave','reason','character varying',255,'NO'),
  ('doctor_leave','status','character varying',10,'NO'),
  ('doctor_leave','approved_by','bigint',NULL,'YES'),
  ('doctor_leave','approved_at','timestamp with time zone',NULL,'YES'),
  ('appointment','appointment_id','bigint',NULL,'NO'),
  ('appointment','patient_id','bigint',NULL,'NO'),
  ('appointment','staff_id','bigint',NULL,'NO'),
  ('appointment','chair_id','bigint',NULL,'NO'),
  ('appointment','date_time','timestamp with time zone',NULL,'NO'),
  ('appointment','duration','smallint',NULL,'NO'),
  ('appointment','end_time','timestamp with time zone',NULL,'YES'),
  ('appointment','status','character varying',12,'NO'),
  ('appointment','source','character varying',10,'NO'),
  ('appointment','booked_by_phone','character varying',15,'YES'),
  ('appointment','hold_until','timestamp with time zone',NULL,'YES'),
  ('appointment','cancelled_by','bigint',NULL,'YES'),
  ('appointment','cancelled_by_type','character varying',10,'YES'),
  ('appointment','cancel_reason','character varying',255,'YES'),
  ('appointment','actual_arrival_at','timestamp with time zone',NULL,'YES'),
  ('appointment','is_priority','boolean',NULL,'NO'),
  ('appointment','note','character varying',500,'YES'),
  ('appointment','created_at','timestamp with time zone',NULL,'NO'),
  ('appointment','created_by','bigint',NULL,'YES'),
  ('appointment_service','appt_service_id','bigint',NULL,'NO'),
  ('appointment_service','appointment_id','bigint',NULL,'NO'),
  ('appointment_service','service_id','bigint',NULL,'YES'),
  ('appointment_service','is_temporary','boolean',NULL,'NO'),
  ('appointment_service','duration_snapshot','smallint',NULL,'NO'),
  ('appointment_service','price_snapshot','numeric',NULL,'YES'),
  ('appointment_service','is_performed','boolean',NULL,'NO'),
  ('treatment_record','record_id','bigint',NULL,'NO'),
  ('treatment_record','appointment_id','bigint',NULL,'NO'),
  ('treatment_record','patient_id','bigint',NULL,'NO'),
  ('treatment_record','staff_id','bigint',NULL,'NO'),
  ('treatment_record','clinical_note','text',NULL,'YES'),
  ('treatment_record','treatment_result','text',NULL,'YES'),
  ('treatment_record','next_appointment_date','date',NULL,'YES'),
  ('treatment_record','created_at','timestamp with time zone',NULL,'NO'),
  ('treatment_progress','progress_id','bigint',NULL,'NO'),
  ('treatment_progress','patient_id','bigint',NULL,'NO'),
  ('treatment_progress','service_id','bigint',NULL,'NO'),
  ('treatment_progress','start_date','date',NULL,'NO'),
  ('treatment_progress','expected_end','date',NULL,'YES'),
  ('treatment_progress','current_session','smallint',NULL,'NO'),
  ('treatment_progress','total_sessions','smallint',NULL,'NO'),
  ('treatment_progress','status','character varying',12,'NO'),
  ('care_task','task_id','bigint',NULL,'NO'),
  ('care_task','appointment_id','bigint',NULL,'YES'),
  ('care_task','patient_id','bigint',NULL,'NO'),
  ('care_task','task_type','character varying',20,'NO'),
  ('care_task','source','character varying',10,'NO'),
  ('care_task','care_scenario_id','bigint',NULL,'YES'),
  ('care_task','assignee_id','bigint',NULL,'NO'),
  ('care_task','created_at','timestamp with time zone',NULL,'NO'),
  ('care_task','scheduled_at','timestamp with time zone',NULL,'NO'),
  ('care_task','sla_deadline','timestamp with time zone',NULL,'NO'),
  ('care_task','status','character varying',12,'NO'),
  ('care_task','is_overdue','boolean',NULL,'NO'),
  ('care_task','result_note','text',NULL,'YES'),
  ('care_task','completed_at','timestamp with time zone',NULL,'YES'),
  ('feedback','feedback_id','bigint',NULL,'NO'),
  ('feedback','appointment_id','bigint',NULL,'NO'),
  ('feedback','customer_id','bigint',NULL,'NO'),
  ('feedback','patient_id','bigint',NULL,'NO'),
  ('feedback','staff_id','bigint',NULL,'NO'),
  ('feedback','rating_score','smallint',NULL,'NO'),
  ('feedback','comment','text',NULL,'YES'),
  ('feedback','alert_flag','boolean',NULL,'NO'),
  ('feedback','is_hidden','boolean',NULL,'NO'),
  ('feedback','hidden_by','bigint',NULL,'YES'),
  ('feedback','hidden_at','timestamp with time zone',NULL,'YES'),
  ('feedback','created_at','timestamp with time zone',NULL,'NO'),
  ('complaint','complaint_id','bigint',NULL,'NO'),
  ('complaint','patient_id','bigint',NULL,'NO'),
  ('complaint','feedback_id','bigint',NULL,'YES'),
  ('complaint','content','text',NULL,'NO'),
  ('complaint','severity','character varying',10,'NO'),
  ('complaint','handler_id','bigint',NULL,'YES'),
  ('complaint','action_taken','text',NULL,'YES'),
  ('complaint','result','text',NULL,'YES'),
  ('complaint','status','character varying',18,'NO'),
  ('complaint','sla_deadline','timestamp with time zone',NULL,'YES'),
  ('complaint','is_hidden','boolean',NULL,'NO'),
  ('complaint','hidden_by','bigint',NULL,'YES'),
  ('complaint','hidden_at','timestamp with time zone',NULL,'YES'),
  ('complaint','created_at','timestamp with time zone',NULL,'NO'),
  ('complaint','closed_at','timestamp with time zone',NULL,'YES'),
  ('audit_log','log_id','bigint',NULL,'NO'),
  ('audit_log','actor_type','character varying',10,'NO'),
  ('audit_log','actor_staff_id','bigint',NULL,'YES'),
  ('audit_log','actor_customer_id','bigint',NULL,'YES'),
  ('audit_log','action_type','character varying',50,'NO'),
  ('audit_log','target_entity','character varying',64,'NO'),
  ('audit_log','target_id','character varying',64,'NO'),
  ('audit_log','old_value','jsonb',NULL,'YES'),
  ('audit_log','new_value','jsonb',NULL,'YES'),
  ('audit_log','ip_address','inet',NULL,'YES'),
  ('audit_log','event_time','timestamp with time zone',NULL,'NO'),
  ('notification_log','notif_id','bigint',NULL,'NO'),
  ('notification_log','customer_id','bigint',NULL,'YES'),
  ('notification_log','appointment_id','bigint',NULL,'YES'),
  ('notification_log','care_task_id','bigint',NULL,'YES'),
  ('notification_log','notif_type','character varying',25,'NO'),
  ('notification_log','channel','character varying',10,'NO'),
  ('notification_log','recipient_phone','character varying',15,'NO'),
  ('notification_log','content','text',NULL,'NO'),
  ('notification_log','status','character varying',10,'NO'),
  ('notification_log','error_code','character varying',50,'YES'),
  ('notification_log','provider_msg_id','character varying',100,'YES'),
  ('notification_log','created_at','timestamp with time zone',NULL,'NO'),
  ('notification_log','sent_at','timestamp with time zone',NULL,'YES'),
  ('no_show_event','event_id','bigint',NULL,'NO'),
  ('no_show_event','patient_id','bigint',NULL,'NO'),
  ('no_show_event','appointment_id','bigint',NULL,'NO'),
  ('no_show_event','occurred_at','timestamp with time zone',NULL,'NO'),
  ('no_show_event','is_reversed','boolean',NULL,'NO'),
  ('no_show_event','reversed_by','bigint',NULL,'YES'),
  ('no_show_event','reversed_at','timestamp with time zone',NULL,'YES'),
  ('no_show_event','reversed_reason','character varying',255,'YES');

CREATE TEMP TABLE exp_constraint (tbl text, name text, kind "char");
INSERT INTO exp_constraint VALUES
  ('role_permission','pk_role_permission','p'),
  ('role_permission','uq_role_permission_role_name','u'),
  ('role_permission','ck_role_name','c'),
  ('staff_account','pk_staff_account','p'),
  ('staff_account','uq_staff_account_username','u'),
  ('staff_account','uq_staff_account_email','u'),
  ('staff_account','uq_staff_account_phone','u'),
  ('staff_account','fk_staff_account_role_id','f'),
  ('clinic_config','pk_clinic_config','p'),
  ('clinic_config','fk_clinic_config_updated_by','f'),
  ('clinic_holiday','pk_clinic_holiday','p'),
  ('clinic_holiday','uq_clinic_holiday_holiday_date','u'),
  ('otp_request','pk_otp_request','p'),
  ('otp_request','ck_otp_purpose','c'),
  ('otp_request','ck_otp_expiry','c'),
  ('otp_request','ck_otp_retry','c'),
  ('customer','pk_customer','p'),
  ('customer','uq_customer_phone','u'),
  ('customer','ck_customer_phone','c'),
  ('patient_profile','pk_patient_profile','p'),
  ('patient_profile','fk_patient_profile_customer_id','f'),
  ('patient_profile','ck_patient_birth_year','c'),
  ('patient_profile','ck_patient_dob_year','c'),
  ('patient_profile','ck_patient_gender','c'),
  ('patient_profile','uq_patient_id_customer','u'),
  ('consent','pk_consent','p'),
  ('consent','fk_consent_customer_id','f'),
  ('consent','fk_consent_for_patient_id','f'),
  ('consent','ck_consent_action','c'),
  ('consent','ck_consent_phone','c'),
  ('opt_out','pk_opt_out','p'),
  ('opt_out','fk_opt_out_customer_id','f'),
  ('opt_out','fk_opt_out_created_by','f'),
  ('opt_out','ck_optout_channel','c'),
  ('opt_out','ck_optout_array','c'),
  ('opt_out','ck_optout_transactional','c'),
  ('dental_chair','pk_dental_chair','p'),
  ('dental_chair','uq_dental_chair_chair_name','u'),
  ('dental_chair','ck_chair_status','c'),
  ('care_scenario','pk_care_scenario','p'),
  ('care_scenario','ck_scenario_category','c'),
  ('care_scenario','ck_scenario_days','c'),
  ('service','pk_service','p'),
  ('service','uq_service_name','u'),
  ('service','fk_service_care_scenario_id','f'),
  ('service','ck_service_duration','c'),
  ('service','ck_service_price','c'),
  ('service','ck_service_cycle','c'),
  ('doctor_schedule','pk_doctor_schedule','p'),
  ('doctor_schedule','fk_doctor_schedule_staff_id','f'),
  ('doctor_schedule','ck_schedule_time','c'),
  ('doctor_schedule','ck_schedule_status','c'),
  ('doctor_schedule','uq_schedule_shift','u'),
  ('doctor_leave','pk_doctor_leave','p'),
  ('doctor_leave','fk_doctor_leave_staff_id','f'),
  ('doctor_leave','fk_doctor_leave_approved_by','f'),
  ('doctor_leave','ck_leave_time','c'),
  ('doctor_leave','ck_leave_status','c'),
  ('doctor_leave','ck_leave_approved','c'),
  ('appointment','pk_appointment','p'),
  ('appointment','fk_appointment_patient_id','f'),
  ('appointment','fk_appointment_staff_id','f'),
  ('appointment','fk_appointment_chair_id','f'),
  ('appointment','fk_appointment_booked_by_phone','f'),
  ('appointment','fk_appointment_cancelled_by','f'),
  ('appointment','fk_appointment_created_by','f'),
  ('appointment','ck_appt_status','c'),
  ('appointment','ck_appt_source','c'),
  ('appointment','ck_appt_duration','c'),
  ('appointment','ck_appt_hold_pending','c'),
  ('appointment','ck_appt_hold_before_start','c'),
  ('appointment','ck_appt_cancel_type','c'),
  ('appointment','ck_appt_cancel_required','c'),
  ('appointment','ck_appt_cancel_staff','c'),
  ('appointment','ck_appt_arrival','c'),
  ('appointment','ck_appt_creator','c'),
  ('appointment','uq_appt_patient','u'),
  ('appointment','uq_appt_patient_staff','u'),
  ('appointment','ex_appt_staff_no_overlap','x'),
  ('appointment','ex_appt_chair_no_overlap','x'),
  ('appointment_service','pk_appointment_service','p'),
  ('appointment_service','fk_appointment_service_appointment_id','f'),
  ('appointment_service','fk_appointment_service_service_id','f'),
  ('appointment_service','ck_apsvc_service','c'),
  ('appointment_service','ck_apsvc_duration','c'),
  ('appointment_service','ck_apsvc_price','c'),
  ('treatment_record','pk_treatment_record','p'),
  ('treatment_record','uq_treatment_record_appointment_id','u'),
  ('treatment_record','fk_treatment_record_appointment_id','f'),
  ('treatment_record','fk_treatment_record_patient_id','f'),
  ('treatment_record','fk_treatment_record_staff_id','f'),
  ('treatment_record','fk_record_appt_patient','f'),
  ('treatment_progress','pk_treatment_progress','p'),
  ('treatment_progress','fk_treatment_progress_patient_id','f'),
  ('treatment_progress','fk_treatment_progress_service_id','f'),
  ('treatment_progress','ck_progress_sessions','c'),
  ('treatment_progress','ck_progress_dates','c'),
  ('treatment_progress','ck_progress_status','c'),
  ('care_task','pk_care_task','p'),
  ('care_task','fk_care_task_appointment_id','f'),
  ('care_task','fk_care_task_patient_id','f'),
  ('care_task','fk_care_task_care_scenario_id','f'),
  ('care_task','fk_care_task_assignee_id','f'),
  ('care_task','ck_task_type','c'),
  ('care_task','ck_task_source','c'),
  ('care_task','ck_task_status','c'),
  ('care_task','ck_task_sla','c'),
  ('care_task','ck_task_closed','c'),
  ('feedback','pk_feedback','p'),
  ('feedback','uq_feedback_appointment_id','u'),
  ('feedback','fk_feedback_appointment_id','f'),
  ('feedback','fk_feedback_customer_id','f'),
  ('feedback','fk_feedback_patient_id','f'),
  ('feedback','fk_feedback_staff_id','f'),
  ('feedback','fk_feedback_hidden_by','f'),
  ('feedback','ck_feedback_rating','c'),
  ('feedback','ck_feedback_hidden','c'),
  ('feedback','fk_feedback_appt','f'),
  ('feedback','fk_feedback_patient_customer','f'),
  ('complaint','pk_complaint','p'),
  ('complaint','fk_complaint_patient_id','f'),
  ('complaint','fk_complaint_feedback_id','f'),
  ('complaint','fk_complaint_handler_id','f'),
  ('complaint','fk_complaint_hidden_by','f'),
  ('complaint','ck_complaint_severity','c'),
  ('complaint','ck_complaint_status','c'),
  ('complaint','ck_complaint_closed','c'),
  ('complaint','ck_complaint_hidden','c'),
  ('audit_log','pk_audit_log','p'),
  ('audit_log','fk_audit_log_actor_staff_id','f'),
  ('audit_log','fk_audit_log_actor_customer_id','f'),
  ('audit_log','ck_audit_actor_type','c'),
  ('audit_log','ck_audit_actor','c'),
  ('notification_log','pk_notification_log','p'),
  ('notification_log','fk_notification_log_customer_id','f'),
  ('notification_log','fk_notification_log_appointment_id','f'),
  ('notification_log','fk_notification_log_care_task_id','f'),
  ('notification_log','ck_notif_type','c'),
  ('notification_log','ck_notif_channel','c'),
  ('notification_log','ck_notif_status','c'),
  ('notification_log','ck_notif_customer','c'),
  ('notification_log','ck_notif_error','c'),
  ('notification_log','ck_notif_sent','c'),
  ('no_show_event','pk_no_show_event','p'),
  ('no_show_event','fk_no_show_event_patient_id','f'),
  ('no_show_event','uq_no_show_event_appointment_id','u'),
  ('no_show_event','fk_no_show_event_appointment_id','f'),
  ('no_show_event','fk_no_show_event_reversed_by','f'),
  ('no_show_event','ck_noshow_reversed','c'),
  ('no_show_event','fk_noshow_appt_patient','f');

CREATE TEMP TABLE exp_index (tbl text, name text);
INSERT INTO exp_index VALUES
  ('otp_request','ix_otp_phone_created'),
  ('patient_profile','ix_patient_customer'),
  ('patient_profile','ix_patient_name'),
  ('consent','ix_consent_phone'),
  ('consent','ix_consent_customer'),
  ('opt_out','ix_optout_customer_active'),
  ('service','ix_service_scenario'),
  ('doctor_leave','ix_leave_staff_date'),
  ('appointment','ix_appt_patient'),
  ('appointment','ix_appt_staff_time'),
  ('appointment','ix_appt_status_time'),
  ('appointment','ix_appt_hold'),
  ('appointment','ix_appt_phone'),
  ('appointment_service','ux_apsvc_appt_service'),
  ('appointment_service','ix_apsvc_service'),
  ('treatment_record','ix_record_patient'),
  ('treatment_progress','ix_progress_patient'),
  ('care_task','ix_task_open_sla'),
  ('care_task','ix_task_assignee'),
  ('care_task','ix_task_patient'),
  ('care_task','ix_task_appt'),
  ('care_task','ix_task_scheduled'),
  ('feedback','ix_feedback_staff'),
  ('feedback','ix_feedback_alert'),
  ('complaint','ux_complaint_feedback'),
  ('complaint','ix_complaint_status'),
  ('complaint','ix_complaint_handler'),
  ('complaint','ix_complaint_patient'),
  ('audit_log','ix_audit_target'),
  ('audit_log','ix_audit_time'),
  ('audit_log','ix_audit_staff'),
  ('audit_log','ix_audit_customer'),
  ('notification_log','ix_notif_customer'),
  ('notification_log','ix_notif_appt'),
  ('notification_log','ix_notif_task'),
  ('notification_log','ix_notif_status_time'),
  ('no_show_event','ix_noshow_patient');

CREATE TEMP TABLE exp_trigger (name text);
INSERT INTO exp_trigger VALUES
  ('trg_audit_log_immutable'),
  ('trg_complaint_nodelete'),
  ('trg_feedback_ai'),
  ('trg_feedback_bi'),
  ('trg_feedback_nodelete'),
  ('trg_no_show_blacklist'),
  ('trg_no_show_guard'),
  ('trg_notification_log_immutable'),
  ('trg_treatment_record_lock');

CREATE TEMP TABLE exp_function (name text);
INSERT INTO exp_function VALUES
  ('add_minutes'),
  ('fn_block_delete'),
  ('fn_block_mutation'),
  ('fn_feedback_after_insert'),
  ('fn_feedback_before_insert'),
  ('fn_no_show_after_change'),
  ('fn_no_show_guard'),
  ('fn_refresh_blacklist'),
  ('fn_treatment_record_lock');

CREATE TEMP TABLE exp_fk (child text, col text, parent text, pcol text);
INSERT INTO exp_fk VALUES
  ('staff_account','role_id','role_permission','role_id'),
  ('clinic_config','updated_by','staff_account','staff_id'),
  ('patient_profile','customer_id','customer','customer_id'),
  ('consent','customer_id','customer','customer_id'),
  ('consent','for_patient_id','patient_profile','patient_id'),
  ('opt_out','customer_id','customer','customer_id'),
  ('opt_out','created_by','staff_account','staff_id'),
  ('service','care_scenario_id','care_scenario','scenario_id'),
  ('doctor_schedule','staff_id','staff_account','staff_id'),
  ('doctor_leave','staff_id','staff_account','staff_id'),
  ('doctor_leave','approved_by','staff_account','staff_id'),
  ('appointment','patient_id','patient_profile','patient_id'),
  ('appointment','staff_id','staff_account','staff_id'),
  ('appointment','chair_id','dental_chair','chair_id'),
  ('appointment','booked_by_phone','customer','phone'),
  ('appointment','cancelled_by','staff_account','staff_id'),
  ('appointment','created_by','staff_account','staff_id'),
  ('appointment_service','appointment_id','appointment','appointment_id'),
  ('appointment_service','service_id','service','service_id'),
  ('treatment_record','appointment_id','appointment','appointment_id'),
  ('treatment_record','patient_id','patient_profile','patient_id'),
  ('treatment_record','staff_id','staff_account','staff_id'),
  ('treatment_progress','patient_id','patient_profile','patient_id'),
  ('treatment_progress','service_id','service','service_id'),
  ('care_task','appointment_id','appointment','appointment_id'),
  ('care_task','patient_id','patient_profile','patient_id'),
  ('care_task','care_scenario_id','care_scenario','scenario_id'),
  ('care_task','assignee_id','staff_account','staff_id'),
  ('feedback','appointment_id','appointment','appointment_id'),
  ('feedback','customer_id','customer','customer_id'),
  ('feedback','patient_id','patient_profile','patient_id'),
  ('feedback','staff_id','staff_account','staff_id'),
  ('feedback','hidden_by','staff_account','staff_id'),
  ('complaint','patient_id','patient_profile','patient_id'),
  ('complaint','feedback_id','feedback','feedback_id'),
  ('complaint','handler_id','staff_account','staff_id'),
  ('complaint','hidden_by','staff_account','staff_id'),
  ('audit_log','actor_staff_id','staff_account','staff_id'),
  ('audit_log','actor_customer_id','customer','customer_id'),
  ('notification_log','customer_id','customer','customer_id'),
  ('notification_log','appointment_id','appointment','appointment_id'),
  ('notification_log','care_task_id','care_task','task_id'),
  ('no_show_event','patient_id','patient_profile','patient_id'),
  ('no_show_event','appointment_id','appointment','appointment_id'),
  ('no_show_event','reversed_by','staff_account','staff_id');

CREATE TEMP TABLE verify_result (seq SERIAL, check_name TEXT, mismatches INTEGER, detail TEXT);

-- 1. Bảng: thiếu / thừa
INSERT INTO verify_result(check_name, mismatches, detail)
SELECT '1. Bảng thiếu/thừa', count(*), string_agg(x, ', ') FROM (
  SELECT 'thiếu ' || e.tbl AS x FROM (SELECT DISTINCT tbl FROM exp_column) e
    LEFT JOIN information_schema.tables t ON t.table_schema = 'public' AND t.table_name = e.tbl AND t.table_type = 'BASE TABLE' WHERE t.table_name IS NULL
  UNION ALL
  SELECT 'thừa ' || t.table_name FROM information_schema.tables t
    WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE' AND t.table_name NOT IN (SELECT tbl FROM exp_column)) d;

-- 2. Cột: thiếu / thừa / sai kiểu / sai độ dài / sai NULL
INSERT INTO verify_result(check_name, mismatches, detail)
SELECT '2. Cột lệch (tên, kiểu, độ dài, NULL)', count(*), string_agg(x, '; ') FROM (
  SELECT 'thiếu ' || e.tbl || '.' || e.col AS x FROM exp_column e
    LEFT JOIN information_schema.columns c ON c.table_schema = 'public' AND c.table_name = e.tbl AND c.column_name = e.col WHERE c.column_name IS NULL
  UNION ALL
  SELECT 'thừa ' || c.table_name || '.' || c.column_name FROM information_schema.columns c
    LEFT JOIN exp_column e ON e.tbl = c.table_name AND e.col = c.column_name
    WHERE c.table_schema = 'public' AND c.table_name IN (SELECT tbl FROM exp_column) AND e.col IS NULL
  UNION ALL
  SELECT 'kiểu ' || e.tbl || '.' || e.col || ' DB=' || c.data_type || ' / DD=' || e.data_type
    FROM exp_column e JOIN information_schema.columns c ON c.table_schema = 'public' AND c.table_name = e.tbl AND c.column_name = e.col
    WHERE c.data_type <> e.data_type
  UNION ALL
  SELECT 'độ dài ' || e.tbl || '.' || e.col || ' DB=' || c.character_maximum_length || ' / DD=' || e.char_len
    FROM exp_column e JOIN information_schema.columns c ON c.table_schema = 'public' AND c.table_name = e.tbl AND c.column_name = e.col
    WHERE e.char_len IS DISTINCT FROM c.character_maximum_length
  UNION ALL
  SELECT 'NULL ' || e.tbl || '.' || e.col || ' DB=' || c.is_nullable || ' / DD=' || e.is_nullable
    FROM exp_column e JOIN information_schema.columns c ON c.table_schema = 'public' AND c.table_name = e.tbl AND c.column_name = e.col
    WHERE c.is_nullable <> e.is_nullable) d;

-- 3. Ràng buộc (PK, UNIQUE, FK, CHECK, EXCLUDE): thiếu / thừa / sai loại
INSERT INTO verify_result(check_name, mismatches, detail)
SELECT '3. Ràng buộc PK/UNIQUE/FK/CHECK/EXCLUDE lệch', count(*), string_agg(x, '; ') FROM (
  SELECT 'thiếu ' || e.tbl || '.' || e.name AS x FROM exp_constraint e
    LEFT JOIN pg_constraint pc ON pc.conname = e.name AND pc.conrelid = ('public.' || e.tbl)::regclass WHERE pc.conname IS NULL
  UNION ALL
  SELECT 'sai loại ' || e.name || ' DB=' || pc.contype FROM exp_constraint e
    JOIN pg_constraint pc ON pc.conname = e.name AND pc.conrelid = ('public.' || e.tbl)::regclass WHERE pc.contype <> e.kind
  UNION ALL
  SELECT 'thừa ' || cl.relname || '.' || pc.conname FROM pg_constraint pc JOIN pg_class cl ON cl.oid = pc.conrelid
    JOIN pg_namespace ns ON ns.oid = cl.relnamespace
    WHERE ns.nspname = 'public' AND pc.contype IN ('p','u','f','c','x') AND cl.relkind = 'r' AND pc.conname NOT IN (SELECT name FROM exp_constraint)) d;

-- 4. Chỉ mục khai báo: thiếu
INSERT INTO verify_result(check_name, mismatches, detail)
SELECT '4. Chỉ mục thiếu', count(*), string_agg(e.name, ', ')
  FROM exp_index e LEFT JOIN pg_indexes i ON i.schemaname = 'public' AND i.indexname = e.name AND i.tablename = e.tbl WHERE i.indexname IS NULL;

-- 5. Mỗi cột FK có chỉ mục dẫn đầu (hỗ trợ JOIN/kiểm tra toàn vẹn) — chỉ báo cảnh báo hiệu năng, không tính lỗi cấu trúc
-- (xem mục 5 trong kết quả riêng bên dưới)

-- 6. Trigger và hàm
INSERT INTO verify_result(check_name, mismatches, detail)
SELECT '6a. Trigger thiếu', count(*), string_agg(e.name, ', ') FROM exp_trigger e
  LEFT JOIN pg_trigger t ON t.tgname = e.name AND NOT t.tgisinternal WHERE t.tgname IS NULL;
INSERT INTO verify_result(check_name, mismatches, detail)
SELECT '6b. Hàm thiếu', count(*), string_agg(e.name, ', ') FROM exp_function e
  LEFT JOIN pg_proc p ON p.proname = e.name AND p.pronamespace = 'public'::regnamespace WHERE p.proname IS NULL;
INSERT INTO verify_result(check_name, mismatches, detail)
SELECT '6c. View v_appointment_leave_conflict thiếu', (SELECT count(*) = 0 FROM pg_views WHERE schemaname = 'public' AND viewname = 'v_appointment_leave_conflict')::int, NULL;

-- 7. Toàn vẹn tham chiếu trên dữ liệu thực (mồ côi) — mỗi FK 1 truy vấn động
CREATE FUNCTION pg_temp.orphans() RETURNS TABLE (fk TEXT, n BIGINT) LANGUAGE plpgsql AS $$
DECLARE r RECORD;
BEGIN
  FOR r IN SELECT * FROM exp_fk LOOP
    RETURN QUERY EXECUTE format(
      'SELECT %L, count(*) FROM %I c WHERE c.%I IS NOT NULL AND NOT EXISTS (SELECT 1 FROM %I p WHERE p.%I = c.%I)',
      r.child || '.' || r.col || ' -> ' || r.parent || '.' || r.pcol, r.child, r.col, r.parent, r.pcol, r.col);
  END LOOP;
END $$;
INSERT INTO verify_result(check_name, mismatches, detail)
SELECT '7. Bản ghi mồ côi (vi phạm FK trên dữ liệu thực)', COALESCE(sum(n), 0)::int, string_agg(fk || '=' || n, '; ') FILTER (WHERE n > 0) FROM pg_temp.orphans();

-- 8. Dữ liệu sai miền giá trị ngoài CHECK (phòng trường hợp CHECK bị tắt/NOT VALID)
INSERT INTO verify_result(check_name, mismatches, detail)
SELECT '8. CHECK/FK chưa được xác thực (NOT VALID)', count(*), string_agg(conname, ', ')
  FROM pg_constraint WHERE connamespace = 'public'::regnamespace AND NOT convalidated;

-- ---------------------------------------------------------------- Kết quả
SELECT '5. FK chưa có chỉ mục dẫn đầu (cảnh báo hiệu năng)' AS check_name, c.conrelid::regclass AS tbl, a.attname AS fk_column
FROM pg_constraint c
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
WHERE c.contype = 'f' AND c.connamespace = 'public'::regnamespace
  AND NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = c.conrelid AND i.indkey[0] = c.conkey[1])
ORDER BY 2, 3;

SELECT seq, check_name, CASE WHEN mismatches = 0 THEN 'PASS' ELSE 'FAIL' END AS result, mismatches, detail FROM verify_result ORDER BY seq;
SELECT (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE') AS so_bang,
       (SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name IN (SELECT tbl FROM exp_column)) AS so_cot,
       (SELECT count(*) FROM pg_constraint WHERE connamespace = 'public'::regnamespace AND contype = 'f') AS so_fk,
       (SELECT count(*) FROM pg_indexes WHERE schemaname = 'public') AS so_index_tong_gom_pk_uq;

DO $$
DECLARE v_fail INTEGER;
BEGIN
  SELECT count(*) INTO v_fail FROM verify_result WHERE mismatches > 0;
  IF v_fail > 0 THEN RAISE EXCEPTION '% mục đối chiếu LỆCH — xem bảng kết quả', v_fail; END IF;
END $$;
