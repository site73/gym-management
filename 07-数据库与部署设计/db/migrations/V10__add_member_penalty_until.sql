-- =============================================================
-- V10：member 增加爽约限制到期时间
-- 背景：SYS-R4 要求累计爽约达 N 次后"限制预约 restrictDays 天"，
--       该限制需要跨请求持久化（原实现只按近 6 个月爽约次数判断，无法表达"剩余 N 天"）。
-- 说明：按 Flyway 规范以新增版本脚本演进。
-- =============================================================

SET NAMES utf8mb4;

ALTER TABLE member
  ADD COLUMN penalty_until DATETIME NULL COMMENT '预约限制截止时间（SYS-R4）' AFTER risk_level;
