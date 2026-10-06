-- =============================================================
-- V5：切片 S5（器材与场地 · 投诉工单）
-- 依据：对象 O5/O8、活动 B6/B8、需求 REQ-B6-001、REQ-B8-007/008
-- =============================================================

SET NAMES utf8mb4;

CREATE TABLE equipment (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  asset_no    VARCHAR(32)  NOT NULL COMMENT '资产编号',
  name        VARCHAR(64)  NOT NULL,
  category    VARCHAR(32)  NULL COMMENT '有氧/力量/其他',
  location    VARCHAR(64)  NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'available'
              COMMENT 'available/borrowed/repair/scrapped',
  purchased_at DATE        NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_equipment_no (asset_no),
  KEY idx_equipment_status (status)
) ENGINE=InnoDB COMMENT='器材台账 O5';

CREATE TABLE equipment_log (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  equipment_id BIGINT      NOT NULL,
  from_status  VARCHAR(16) NULL,
  to_status    VARCHAR(16) NOT NULL,
  operator_id  BIGINT      NULL,
  remark       VARCHAR(255) NULL,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_eq_log_equipment (equipment_id, created_at)
) ENGINE=InnoDB COMMENT='器材状态变更流水';

CREATE TABLE ticket (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  ticket_no   VARCHAR(32)  NOT NULL,
  member_id   BIGINT       NULL,
  type        VARCHAR(16)  NOT NULL COMMENT 'complaint/repair/injury',
  source      VARCHAR(16)  NOT NULL DEFAULT 'app' COMMENT 'app/front_desk/phone',
  content     VARCHAR(512) NOT NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'pending'
              COMMENT 'pending/processing/closed',
  handler_id  BIGINT       NULL,
  result      VARCHAR(512) NULL COMMENT '关闭前必填（REQ-B8-008）',
  closed_at   DATETIME     NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket_no (ticket_no),
  KEY idx_ticket_status (status, created_at)
) ENGINE=InnoDB COMMENT='工单 O8（投诉/报修/受伤）';

CREATE TABLE ticket_log (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  ticket_id  BIGINT       NOT NULL,
  action     VARCHAR(32)  NOT NULL COMMENT 'create/accept/comment/close',
  content    VARCHAR(512) NULL,
  operator_id BIGINT      NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ticket_log (ticket_id, created_at)
) ENGINE=InnoDB COMMENT='工单处理过程留痕';
