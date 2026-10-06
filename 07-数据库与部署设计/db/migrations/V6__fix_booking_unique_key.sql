-- =============================================================
-- V6：修正 booking 的唯一约束
-- 背景：V1 中 uk_booking_member_course (member_id, course_id) 过严，
--       导致"取消预约后无法再次预约同一课程"（属合法业务行为）。
-- 处理：删除唯一约束，改为普通索引；幂等性改由应用层保证
--       （仅对 booked / checked_in / no_show 判重，见 BookingAppService）。
-- 说明：按 Flyway 规范，不修改历史脚本 V1，以新增版本脚本修正。
-- =============================================================

SET NAMES utf8mb4;

ALTER TABLE booking DROP INDEX uk_booking_member_course;

CREATE INDEX idx_booking_member_course ON booking (member_id, course_id);
