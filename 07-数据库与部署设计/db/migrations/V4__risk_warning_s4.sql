-- =============================================================
-- V4：切片 S4（会员流失风险评分 · 爽约预测 · 预警任务）—— 微创新点
-- 依据：业务活动 A7/A8/A9、规则 SYS-R7/SYS-R8、需求 REQ-B8-001~006
-- =============================================================

SET NAMES utf8mb4;

CREATE TABLE risk_score (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  member_id    BIGINT       NOT NULL,
  score_date   DATE         NOT NULL,
  risk_level   VARCHAR(16)  NOT NULL COMMENT 'low/mid/high',
  hit_rule     VARCHAR(64)  NULL COMMENT '命中条件：连续4周未到店 / 30天爽约率>30%',
  no_show_rate DECIMAL(5,4) NULL COMMENT '近30天爽约率',
  last_visit_days INT       NULL COMMENT '距最近到店的间隔天数',
  detail_json  JSON         NULL COMMENT '评分明细，便于人工核查（可解释性）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_risk_member_date (member_id, score_date),
  KEY idx_risk_level_date (risk_level, score_date)
) ENGINE=InnoDB COMMENT='会员流失风险评分（SYS-R7，含判定依据以保证可解释）';

CREATE TABLE warning_task (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  member_id   BIGINT       NOT NULL,
  biz_type    VARCHAR(32)  NOT NULL COMMENT 'churn_risk/no_show_risk',
  rule_code   VARCHAR(32)  NOT NULL COMMENT 'SYS-R7/SYS-R8',
  level       VARCHAR(16)  NOT NULL DEFAULT 'normal',
  assignee_id BIGINT       NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'pending'
              COMMENT 'pending/processing/done/escalated',
  escalated_at DATETIME    NULL COMMENT '超48h未处理升级至店长（REQ-B8-006）',
  due_at      DATETIME     NULL,
  result      VARCHAR(255) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_warn_status_due (status, due_at),
  KEY idx_warn_member (member_id, biz_type)
) ENGINE=InnoDB COMMENT='预警任务（SYS-R7/R8，含超时升级）';

CREATE TABLE booking_prediction (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  booking_id  BIGINT       NOT NULL,
  course_id   BIGINT       NOT NULL,
  prob        DECIMAL(4,3) NOT NULL COMMENT '预测爽约概率 0~1',
  action      VARCHAR(16)  NOT NULL COMMENT 'remind/release/none',
  predicted_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  model_ver   VARCHAR(16)  NOT NULL DEFAULT 'rule-v1' COMMENT '先规则式，后模型演进',
  detail_json JSON         NULL COMMENT '特征明细，便于解释与回溯',
  PRIMARY KEY (id),
  UNIQUE KEY uk_pred_booking (booking_id),
  KEY idx_pred_course (course_id)
) ENGINE=InnoDB COMMENT='爽约预测结果（SYS-R8，T=0.6 决策：提醒/释放名额）';
