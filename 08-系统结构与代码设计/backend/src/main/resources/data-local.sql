-- 本地运行用种子数据（H2 内存库，每次启动重建后执行）
-- 与 07-数据库与部署设计/db/seed/R__seed_base_data.sql 的规则参数保持一致

-- 规则参数（SYS-R1–R10）
INSERT INTO rule_config (id, rule_code, rule_name, params_json, enabled, version) VALUES
 (1,  'SYS-R1',  '会籍有效期校验',   '{"requireStatus":"active"}', 1, 1),
 (2,  'SYS-R2',  '请假冻结',         '{"maxDays":90}', 1, 1),
 (3,  'SYS-R3',  '约课上限与冲突',   '{"capacityCheck":true,"timeConflictCheck":true}', 1, 1),
 (4,  'SYS-R4',  '爽约惩罚',         '{"N":3,"restrictDays":7}', 1, 1),
 (5,  'SYS-R5',  '私教课包核销',     '{"deductPerClass":1}', 1, 1),
 (6,  'SYS-R6',  '到期提醒',         '{"D":7}', 1, 1),
 (7,  'SYS-R7',  '流失风险规则',     '{"noVisitWeeks":4,"noShowRate":0.30,"windowDays":30}', 1, 1),
 (8,  'SYS-R8',  '爽约预测规则',     '{"T":0.60}', 1, 1),
 (9,  'SYS-R9',  '新会员首月跟进',   '{"days":30,"minVisits":2}', 1, 1),
 (10, 'SYS-R10', '私教业绩提成',     '{"calcType":"by_times","rate":0.20}', 1, 1);

-- 会员
INSERT INTO member (id, member_no, name, phone, status, risk_level, created_at) VALUES
 (1, 'M001', '张三', '138****0001', 'active',  NULL, CURRENT_TIMESTAMP),
 (2, 'M002', '李四', '139****0002', 'expired', NULL, CURRENT_TIMESTAMP),
 (3, 'M003', '王五', '137****0003', 'active',  NULL, CURRENT_TIMESTAMP);

-- 会籍 / 课包（张三有 1 次私教课包，用于验证 SYS-R5 核销）
INSERT INTO membership (id, member_id, type, start_date, end_date, status, remaining_times) VALUES
 (1, 1, 'year',       CURRENT_DATE, DATEADD('YEAR', 1, CURRENT_DATE), 'active', NULL),
 (2, 1, 'pt_package', CURRENT_DATE, DATEADD('MONTH', 3, CURRENT_DATE), 'active', 1),
 (3, 3, 'year',       CURRENT_DATE, DATEADD('YEAR', 1, CURRENT_DATE), 'active', NULL);

-- 课程（12 号瑜伽已满员，用于验证 SYS-R3 容量校验）
INSERT INTO course (id, code, name, type, coach_id, room, start_time, end_time, capacity, booked_count, status) VALUES
 (11, 'C001', '动感单车',   'group', 101, 'A 厅', DATEADD('HOUR', 19, CURRENT_DATE), DATEADD('HOUR', 20, CURRENT_DATE), 20, 18, 'published'),
 (12, 'C002', '瑜伽',       'group', 102, 'B 厅', DATEADD('HOUR', 18, CURRENT_DATE), DATEADD('HOUR', 19, CURRENT_DATE), 15, 15, 'full'),
 (13, 'C003', '搏击操',     'group', 101, 'A 厅', DATEADD('HOUR', 20, CURRENT_DATE), DATEADD('HOUR', 21, CURRENT_DATE), 12,  5, 'published');
