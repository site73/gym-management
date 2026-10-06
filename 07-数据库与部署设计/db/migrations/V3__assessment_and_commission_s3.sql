-- =============================================================
-- V3：切片 S3（体测档案 · 体验转化跟进 · 私教业绩提成）
-- 依据：业务活动 A2/A11、规则 SYS-R9/SYS-R10、对象 O6
-- =============================================================

SET NAMES utf8mb4;

CREATE TABLE assessment (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  member_id   BIGINT       NOT NULL,
  assess_date DATE         NOT NULL,
  height_cm   DECIMAL(5,1) NULL,
  weight_kg   DECIMAL(5,1) NULL,
  bmi         DECIMAL(4,1) NULL COMMENT '可由系统计算',
  body_fat    DECIMAL(4,1) NULL COMMENT '体脂率 %',
  measure_json JSON        NULL COMMENT '围度：{"胸":..,"腰":..,"臀":..,"臂":..}',
  coach_id    BIGINT       NULL,
  remark      VARCHAR(255) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by  BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_assess_member_date (member_id, assess_date)
) ENGINE=InnoDB COMMENT='体测记录 O6';

CREATE TABLE follow_task (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  member_id   BIGINT       NOT NULL,
  task_type   VARCHAR(32)  NOT NULL COMMENT 'new_member_30d/risk_high/manual',
  rule_code   VARCHAR(32)  NULL COMMENT '来源规则，如 SYS-R9/SYS-R7',
  assignee_id BIGINT       NULL COMMENT '会籍顾问',
  status      VARCHAR(16)  NOT NULL DEFAULT 'pending'
              COMMENT 'pending/processing/done/overdue',
  priority    VARCHAR(8)   NOT NULL DEFAULT 'normal',
  due_at      DATETIME     NULL,
  result      VARCHAR(255) NULL COMMENT '跟进结果',
  done_at     DATETIME     NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_follow_assignee (assignee_id, status),
  KEY idx_follow_member (member_id, task_type)
) ENGINE=InnoDB COMMENT='跟进任务（SYS-R9 首月跟进 / SYS-R7 高风险跟进）';

CREATE TABLE commission_rule (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  coach_id     BIGINT       NULL COMMENT '空表示默认规则',
  calc_type    VARCHAR(16)  NOT NULL COMMENT 'by_times/by_amount',
  rate         DECIMAL(5,4) NOT NULL COMMENT '提成比例，如 0.2000',
  effective_from DATE      NOT NULL,
  effective_to   DATE      NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by   BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_comm_rule_coach (coach_id, effective_from)
) ENGINE=InnoDB COMMENT='提成规则（店长配置，SYS-R10）';

CREATE TABLE commission_record (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  coach_id     BIGINT        NOT NULL,
  period       VARCHAR(7)    NOT NULL COMMENT 'yyyy-MM',
  consumed_times INT         NOT NULL DEFAULT 0,
  consumed_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  rate         DECIMAL(5,4)  NOT NULL,
  commission   DECIMAL(12,2) NOT NULL,
  status       VARCHAR(16)   NOT NULL DEFAULT 'draft'
               COMMENT 'draft/confirmed/disputed',
  confirmed_by BIGINT        NULL,
  confirmed_at DATETIME      NULL,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_comm_coach_period (coach_id, period)
) ENGINE=InnoDB COMMENT='提成核算记录（A11 输出，可下钻到课消明细）';

CREATE TABLE course_consumption (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  booking_id  BIGINT       NOT NULL COMMENT '关联预约/上课记录',
  member_id   BIGINT       NOT NULL,
  coach_id    BIGINT       NOT NULL,
  course_id   BIGINT       NOT NULL,
  consume_at  DATETIME     NOT NULL,
  amount      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '课消金额（用于按额提成）',
  times       INT          NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  KEY idx_consume_coach_time (coach_id, consume_at),
  KEY idx_consume_booking (booking_id)
) ENGINE=InnoDB COMMENT='课消明细（提成下钻依据）';
