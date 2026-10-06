-- =============================================================
-- R__ 可重复执行的基础数据（初始化范围，见《业务数据初始化与迁移方案》第（1）部分）
-- 幂等：使用 INSERT ... ON DUPLICATE KEY UPDATE
-- =============================================================

SET NAMES utf8mb4;

-- 1) 角色
INSERT INTO sys_role (code, name) VALUES
  ('member',      '会员'),
  ('staff_sales', '会籍顾问'),
  ('pt',          '私教'),
  ('finance',     '财务'),
  ('manager',     '店长'),
  ('admin',       '系统管理员')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2) 字典：状态与类型枚举（与状态机一致）
INSERT INTO dict_item (dict_type, item_code, item_name, sort_no) VALUES
  ('member_status','potential','潜在',1),
  ('member_status','active','有效',2),
  ('member_status','frozen','冻结',3),
  ('member_status','expired','过期',4),
  ('member_status','lost','流失',5),
  ('booking_status','booked','已约',1),
  ('booking_status','checked_in','已签到',2),
  ('booking_status','no_show','爽约',3),
  ('booking_status','cancelled','取消',4),
  ('course_status','published','已发布',1),
  ('course_status','full','满员',2),
  ('course_status','ongoing','进行中',3),
  ('course_status','finished','已结束',4),
  ('order_status','pending','待支付',1),
  ('order_status','paid','已支付',2),
  ('order_status','cancelled','已取消',3),
  ('order_status','refunded','已退款',4),
  ('order_status','abnormal','异常',5),
  ('ticket_status','pending','待处理',1),
  ('ticket_status','processing','处理中',2),
  ('ticket_status','closed','已关闭',3)
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 3) 规则参数（SYS-R1..R10），参数变更须递增 version
INSERT INTO rule_config (rule_code, rule_name, params_json, enabled) VALUES
  ('SYS-R1','会籍有效期校验',           JSON_OBJECT('requireStatus','active'), 1),
  ('SYS-R2','请假冻结',                 JSON_OBJECT('maxDays',90), 1),
  ('SYS-R3','约课上限与冲突',           JSON_OBJECT('capacityCheck',true,'timeConflictCheck',true), 1),
  ('SYS-R4','爽约惩罚',                 JSON_OBJECT('N',3,'restrictDays',7), 1),
  ('SYS-R5','私教课包核销',             JSON_OBJECT('deductPerClass',1), 1),
  ('SYS-R6','到期提醒',                 JSON_OBJECT('D',7), 1),
  ('SYS-R7','流失风险规则',             JSON_OBJECT('noVisitWeeks',4,'noShowRate',0.30,'windowDays',30), 1),
  ('SYS-R8','爽约预测规则',             JSON_OBJECT('T',0.60,'action','remind_or_release'), 1),
  ('SYS-R9','新会员首月跟进',           JSON_OBJECT('days',30,'minVisits',2), 1),
  ('SYS-R10','私教业绩提成',            JSON_OBJECT('calcType','by_times','rate',0.20), 1)
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name), params_json = VALUES(params_json);

-- 4) 课程目录（基础数据示例：课名/类型/默认时长/默认容量）
--    实际排课由后台创建，此处仅初始化"课程目录"字典
INSERT INTO dict_item (dict_type, item_code, item_name, sort_no) VALUES
  ('course_catalog','spinning','动感单车',1),
  ('course_catalog','yoga','瑜伽',2),
  ('course_catalog','boxing','搏击操',3),
  ('course_catalog','bodypump','杠铃操',4),
  ('course_catalog','pt_basic','私教基础课',5),
  ('equipment_category','cardio','有氧器械',1),
  ('equipment_category','strength','力量器械',2),
  ('equipment_category','other','其他',3)
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);
