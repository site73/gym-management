-- =============================================================
-- V7：payment_order 增加课包次数字段（S2 切片）
-- 背景：私教课包订单需要记录"购买次数"，支付成功后据此增加课包余次（SYS-R5/R10 依赖）。
-- 说明：按 Flyway 规范以新增版本脚本演进，不修改历史脚本。
-- =============================================================

SET NAMES utf8mb4;

ALTER TABLE payment_order
  ADD COLUMN times INT NULL COMMENT '课包次数（biz_type=pt_package 时有效）' AFTER biz_type;
