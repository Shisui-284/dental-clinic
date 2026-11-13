-- =====================================================================
-- 00_create_database.sql — Khởi tạo database dental_clinic
-- Chạy bằng tài khoản có quyền CREATEDB, kết nối vào database 'postgres':
--   psql -U postgres -d postgres -f 00_create_database.sql
-- Yêu cầu: PostgreSQL 14+. Extension btree_gist (dùng ở 01_schema.sql) là extension "trusted"
-- từ PostgreSQL 13, owner của database tạo được; nếu bản thấp hơn cần superuser.
-- =====================================================================
\set ON_ERROR_STOP on

-- Chỉ tạo nếu chưa tồn tại (\gexec chạy câu lệnh do SELECT sinh ra)
SELECT 'CREATE DATABASE dental_clinic ENCODING ''UTF8'' TEMPLATE template0'
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'dental_clinic') \gexec

-- Múi giờ hiển thị mặc định của database. Dữ liệu TIMESTAMPTZ vẫn lưu UTC.
ALTER DATABASE dental_clinic SET timezone TO 'Asia/Ho_Chi_Minh';
COMMENT ON DATABASE dental_clinic IS 'Hệ thống đặt lịch hẹn & chăm sóc khách hàng — Phòng khám nha khoa CHIA';
