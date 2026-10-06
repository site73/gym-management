-- =============================================================
-- V8：audit_log 增加 detail 字段
-- 背景：审计日志需要记录可读的操作详情（如"member=1,course=11"），
--       便于问题追溯；原表仅有 before/after JSON 字段。
-- 说明：按 Flyway 规范以新增版本脚本演进。
-- =============================================================

SET NAMES utf8mb4;

ALTER TABLE audit_log
  ADD COLUMN detail VARCHAR(512) NULL COMMENT '操作详情（可读）' AFTER action;
