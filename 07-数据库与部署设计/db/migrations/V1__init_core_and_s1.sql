-- =============================================================
-- V1 初始化：系统支撑 + 切片 S1（会员·会籍·课程·预约签到）
-- 数据库：MySQL 8.0 / utf8mb4
-- 说明：由业务对象 O1-O4 与状态机推导；所有表含审计列与软删除
-- =============================================================

SET NAMES utf8mb4;

-- ---------- 系统支撑（M0） ----------
CREATE TABLE sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL COMMENT '登录名（手机号或工号）',
  password_hash VARCHAR(128) NOT NULL,
  display_name  VARCHAR(64)  NOT NULL,
  phone         VARCHAR(20)  NULL COMMENT '脱敏展示',
  status        VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by    BIGINT       NULL,
  updated_by    BIGINT       NULL,
  deleted_at    DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB COMMENT='系统用户（店长/会籍/私教/财务/管理员）';

CREATE TABLE sys_role (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  code       VARCHAR(32) NOT NULL COMMENT 'member/staff_sales/pt/finance/manager/admin',
  name       VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB COMMENT='系统角色';

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB COMMENT='用户-角色';

CREATE TABLE dict_item (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  dict_type  VARCHAR(64) NOT NULL COMMENT '字典类型，如 membership_status',
  item_code  VARCHAR(64) NOT NULL,
  item_name  VARCHAR(64) NOT NULL,
  sort_no    INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict (dict_type, item_code)
) ENGINE=InnoDB COMMENT='字典（状态/类型等枚举）';

CREATE TABLE rule_config (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  rule_code   VARCHAR(32)  NOT NULL COMMENT 'SYS-R1..SYS-R10',
  rule_name   VARCHAR(64)  NOT NULL,
  params_json JSON         NOT NULL COMMENT '规则参数，如 {"N":3,"days":7}',
  enabled     TINYINT(1)   NOT NULL DEFAULT 1,
  version     INT          NOT NULL DEFAULT 1 COMMENT '参数版本，变更递增',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by  BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule_code (rule_code)
) ENGINE=InnoDB COMMENT='业务规则参数（可配置，变更留版本）';

CREATE TABLE audit_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  actor_id    BIGINT       NULL,
  actor_role  VARCHAR(32)  NULL,
  action      VARCHAR(64)  NOT NULL COMMENT '如 booking.create / membership.freeze',
  target_type VARCHAR(32)  NOT NULL,
  target_id   BIGINT       NULL,
  before_json JSON         NULL,
  after_json  JSON         NULL,
  ip          VARCHAR(45)  NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_target (target_type, target_id),
  KEY idx_audit_actor (actor_id, created_at)
) ENGINE=InnoDB COMMENT='审计日志（权限/状态/财务相关操作必留痕）';

-- ---------- S1：会员与会籍 ----------
CREATE TABLE member (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  member_no    VARCHAR(32) NOT NULL COMMENT '会员编号',
  name         VARCHAR(64) NOT NULL,
  phone        VARCHAR(20) NULL COMMENT '加密存储，展示脱敏',
  gender       VARCHAR(8)  NULL,
  birthday     DATE        NULL,
  status       VARCHAR(16) NOT NULL DEFAULT 'potential'
               COMMENT 'potential/active/frozen/expired/lost',
  risk_level   VARCHAR(16) NULL COMMENT 'low/mid/high（由 SYS-R7 计算）',
  source       VARCHAR(32) NULL COMMENT '来源渠道',
  user_id      BIGINT      NULL COMMENT '关联小程序账号',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by   BIGINT      NULL,
  updated_by   BIGINT      NULL,
  deleted_at   DATETIME    NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_no (member_no),
  KEY idx_member_status (status),
  KEY idx_member_risk (risk_level)
) ENGINE=InnoDB COMMENT='会员 O1';

CREATE TABLE membership (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  member_id        BIGINT       NOT NULL,
  type             VARCHAR(16)  NOT NULL COMMENT 'month/quarter/year',
  start_date       DATE         NOT NULL,
  end_date         DATE         NOT NULL,
  status           VARCHAR(16)  NOT NULL DEFAULT 'active'
                   COMMENT 'active/frozen/expired/cancelled',
  remaining_times  INT          NULL COMMENT '次卡剩余次数；期卡为空',
  frozen_from      DATE         NULL,
  frozen_to        DATE         NULL,
  order_id         BIGINT       NULL COMMENT '来源订单',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by       BIGINT       NULL,
  updated_by       BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_ms_member (member_id, status),
  KEY idx_ms_end_date (end_date)
) ENGINE=InnoDB COMMENT='会籍合同 O2';

-- ---------- S1：课程与排课 ----------
CREATE TABLE course (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  code          VARCHAR(32)  NOT NULL,
  name          VARCHAR(64)  NOT NULL,
  type          VARCHAR(16)  NOT NULL COMMENT 'group/pt',
  coach_id      BIGINT       NULL COMMENT '私教或带课教练',
  room          VARCHAR(32)  NULL COMMENT '场地',
  start_time    DATETIME     NOT NULL,
  end_time      DATETIME     NOT NULL,
  capacity      INT          NOT NULL DEFAULT 0,
  booked_count  INT          NOT NULL DEFAULT 0 COMMENT '已约人数（冗余，便于容量校验）',
  status        VARCHAR(16)  NOT NULL DEFAULT 'published'
                COMMENT 'published/full/ongoing/finished/cancelled',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by    BIGINT       NULL,
  updated_by    BIGINT       NULL,
  deleted_at    DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_code (code),
  KEY idx_course_coach_time (coach_id, start_time) COMMENT 'SYS-R3 排课冲突检测',
  KEY idx_course_room_time (room, start_time)
) ENGINE=InnoDB COMMENT='课程/排课 O3';

-- ---------- S1：预约与签到 ----------
CREATE TABLE booking (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  member_id       BIGINT       NOT NULL,
  course_id       BIGINT       NOT NULL,
  status          VARCHAR(16)  NOT NULL DEFAULT 'booked'
                  COMMENT 'booked/checked_in/no_show/cancelled',
  no_show_prob    DECIMAL(4,3) NULL COMMENT 'SYS-R8 爽约概率 0~1',
  booked_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  checkin_at      DATETIME     NULL,
  checkin_channel VARCHAR(16)  NULL COMMENT 'scan/front_desk（前台代签）',
  operator_id     BIGINT       NULL COMMENT '代签/代约的操作人',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_booking_member_course (member_id, course_id) COMMENT '防重复提交（幂等）',
  KEY idx_booking_course_status (course_id, status),
  KEY idx_booking_member_time (member_id, booked_at)
) ENGINE=InnoDB COMMENT='预约 O4（含签到与爽约）';
