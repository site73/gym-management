-- =============================================================
-- V2：切片 S2（收费续费 · 提醒 · 对账）
-- 依据：业务活动 A1/A10、规则 SYS-R6、异常补偿 REQ-B5-004
-- =============================================================

SET NAMES utf8mb4;

CREATE TABLE payment_order (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  order_no      VARCHAR(40)   NOT NULL COMMENT '对外订单号',
  member_id     BIGINT        NOT NULL,
  biz_type      VARCHAR(16)   NOT NULL COMMENT 'membership/pt_package',
  ref_id        BIGINT        NULL COMMENT '关联会籍或课包',
  amount        DECIMAL(10,2) NOT NULL,
  paid_amount   DECIMAL(10,2) NULL,
  channel       VARCHAR(16)   NOT NULL DEFAULT 'wechat' COMMENT 'wechat/cash/other',
  status        VARCHAR(16)   NOT NULL DEFAULT 'pending'
                COMMENT 'pending/paid/cancelled/refunded/abnormal',
  out_trade_no  VARCHAR(64)   NULL COMMENT '第三方交易号',
  paid_at       DATETIME      NULL,
  abnormal_reason VARCHAR(255) NULL COMMENT '金额不符/回调失败原因',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by    BIGINT        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_out_trade_no (out_trade_no),
  KEY idx_order_member (member_id, status),
  KEY idx_order_status_time (status, created_at)
) ENGINE=InnoDB COMMENT='收费订单 O7（含异常订单）';

CREATE TABLE settlement (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  period       VARCHAR(7)   NOT NULL COMMENT 'yyyy-MM',
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  order_count  INT          NOT NULL DEFAULT 0,
  abnormal_count INT        NOT NULL DEFAULT 0,
  status       VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/confirmed',
  confirmed_by BIGINT       NULL,
  confirmed_at DATETIME     NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_settlement_period (period)
) ENGINE=InnoDB COMMENT='月度对账单（A10 输出）';

CREATE TABLE notification (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  member_id   BIGINT       NULL,
  user_id     BIGINT       NULL,
  channel     VARCHAR(16)  NOT NULL COMMENT 'wx_subscribe/sms/inapp',
  template    VARCHAR(64)  NOT NULL COMMENT 'renew_reminder/no_show_reminder/...',
  content     VARCHAR(512) NOT NULL,
  biz_type    VARCHAR(32)  NULL COMMENT '来源业务，如 SYS-R6',
  biz_id      BIGINT       NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/sent/failed',
  fail_reason VARCHAR(255) NULL,
  retry_count INT          NOT NULL DEFAULT 0,
  sent_at     DATETIME     NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notify_member (member_id, created_at),
  KEY idx_notify_status (status, created_at)
) ENGINE=InnoDB COMMENT='提醒通知（SYS-R6 到期提醒等，含重试与降级）';
