-- V13 · 演示会员名册（配合"报名名单与余位严格闭合"）
--
-- 背景：种子脚本把演示课程的 booked_count 预设为 18/15/5，但没有对应的 booking 行，
--      导致「课程显示 15/15 已满，报名名单却是共 0 人」的账实不符。
-- 本脚本提供**真实存在的会员档案**（source='demo'），由 DemoScheduleAligner 在启动/重置后
-- 为演示课程补齐 booking 行，并把所有课程的 booked_count 重算为有效预约数——保证闭合。
--
-- 幂等：用固定主键 + INSERT IGNORE，重复执行不会产生重复档案。

INSERT IGNORE INTO member (id, member_no, name, phone, gender, status, risk_level, source) VALUES
  (101, 'M101', '陈志强', '137****0101', '男', 'active', 'low',    'demo'),
  (102, 'M102', '王丽娜', '137****0102', '女', 'active', 'low',    'demo'),
  (103, 'M103', '李世杰', '137****0103', '男', 'active', 'low',    'demo'),
  (104, 'M104', '赵欣然', '137****0104', '女', 'active', 'medium', 'demo'),
  (105, 'M105', '孙浩然', '137****0105', '男', 'active', 'low',    'demo'),
  (106, 'M106', '周雨桐', '137****0106', '女', 'active', 'low',    'demo'),
  (107, 'M107', '吴博文', '137****0107', '男', 'active', 'low',    'demo'),
  (108, 'M108', '郑晓萌', '137****0108', '女', 'active', 'medium', 'demo'),
  (109, 'M109', '冯天宇', '137****0109', '男', 'active', 'low',    'demo'),
  (110, 'M110', '陈静怡', '137****0110', '女', 'active', 'low',    'demo'),
  (111, 'M111', '褚建军', '137****0111', '男', 'active', 'low',    'demo'),
  (112, 'M112', '卫思琪', '137****0112', '女', 'active', 'low',    'demo'),
  (113, 'M113', '蒋文博', '137****0113', '男', 'active', 'medium', 'demo'),
  (114, 'M114', '沈梦洁', '137****0114', '女', 'active', 'low',    'demo'),
  (115, 'M115', '韩明轩', '137****0115', '男', 'active', 'low',    'demo'),
  (116, 'M116', '杨若曦', '137****0116', '女', 'active', 'low',    'demo'),
  (117, 'M117', '朱俊杰', '137****0117', '男', 'active', 'low',    'demo'),
  (118, 'M118', '秦雅琴', '137****0118', '女', 'active', 'medium', 'demo'),
  (119, 'M119', '尤志远', '137****0119', '男', 'active', 'low',    'demo'),
  (120, 'M120', '许诗涵', '137****0120', '女', 'active', 'low',    'demo');

-- 演示会员全部持有效年卡（配合"私有场馆仅对有效会籍开放"的规则演示）
INSERT IGNORE INTO membership (id, member_id, type, start_date, end_date, status)
SELECT id, id, 'year', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), 'active'
FROM member WHERE source = 'demo';
