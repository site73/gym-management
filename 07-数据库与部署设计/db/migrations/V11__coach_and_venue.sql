-- =============================================================
-- V11：教练档案 + 场馆与场地预约
-- 背景：本次新增三类业务对象
--   1) 教练需要独立档案与登录账号（原 course.coach_id 只是一个裸数字）
--   2) 场馆共 5 处：4 个私有场馆 + 1 个公共区域
--   3) 场地预约需要按"同一场地时段不可重叠"做冲突校验（复用 SYS-R3 的区间重叠思路）
-- 说明：教练主键显式指定 101/102/103，与既有 course.coach_id 取值保持一致，
--       无需改写已有课程数据。
-- =============================================================

SET NAMES utf8mb4;

-- ---------- 教练档案 ----------
CREATE TABLE coach (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  code       VARCHAR(32) NOT NULL COMMENT '教练编号，如 K001',
  name       VARCHAR(64) NOT NULL,
  phone      VARCHAR(20) NULL,
  specialty  VARCHAR(64) NULL COMMENT '擅长项目',
  status     VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active 在职 / leave 休假',
  user_id    BIGINT      NULL COMMENT '关联的登录账号（sys_user.id）',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coach_code (code)
) COMMENT '教练档案';

-- ---------- 场馆 / 区域 ----------
CREATE TABLE venue (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  code        VARCHAR(32)   NOT NULL COMMENT '场馆编号，如 V01',
  name        VARCHAR(64)   NOT NULL,
  type        VARCHAR(16)   NOT NULL COMMENT 'private 私有场馆 / public 公共区域',
  capacity    INT           NOT NULL DEFAULT 1 COMMENT '可容纳人数',
  location    VARCHAR(64)   NULL COMMENT '位置说明',
  hourly_fee  DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '按时使用费（0 表示免费）',
  status      VARCHAR(16)   NOT NULL DEFAULT 'available' COMMENT 'available 可用 / maintenance 维护中',
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_venue_code (code)
) COMMENT '场馆 / 区域';

-- ---------- 场地预约 ----------
CREATE TABLE venue_booking (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  venue_id    BIGINT      NOT NULL,
  member_id   BIGINT      NOT NULL,
  start_time  DATETIME    NOT NULL,
  end_time    DATETIME    NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'booked' COMMENT 'booked 已预约 / cancelled 已取消 / finished 已结束',
  created_by  BIGINT      NULL COMMENT '发起人：会员自助为空，门店代约为店员 ID',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_venue_time (venue_id, start_time) COMMENT '时段冲突检测',
  KEY idx_member_time (member_id, start_time)
) COMMENT '场地预约';

-- ---------- 教练角色 ----------
INSERT INTO sys_role (code, name) VALUES ('coach', '教练')
  ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ---------- 种子：教练（主键与既有 course.coach_id 对齐）----------
INSERT INTO coach (id, code, name, phone, specialty, status) VALUES
  (101, 'K001', '王教练', '13800000001', '力量训练 / 体态矫正', 'active'),
  (102, 'K002', '李教练', '13800000002', '团体课程 / 普拉提',   'active'),
  (103, 'K003', '赵教练', '13800000003', '搏击 / 体能训练',     'active');

-- ---------- 种子：4 个私有场馆 + 1 个公共区域 ----------
INSERT INTO venue (code, name, type, capacity, location, hourly_fee, status) VALUES
  ('V01', '私教 A 室',    'private', 2,  '二层东侧',  120.00, 'available'),
  ('V02', '私教 B 室',    'private', 2,  '二层东侧',  120.00, 'available'),
  ('V03', '瑜伽小班室',   'private', 8,  '二层西侧',   80.00, 'available'),
  ('V04', '力量训练室',   'private', 6,  '一层北侧',  100.00, 'available'),
  ('V05', '公共自由训练区', 'public', 30, '一层大厅',     0.00, 'available');
