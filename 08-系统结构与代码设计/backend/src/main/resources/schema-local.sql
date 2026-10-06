-- H2 本地模式：sys_* 三张表不由实体映射（由 Flyway 在 MySQL 模式创建），
-- 这里以 H2 语法补建，保证 local profile 也能登录。
CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  display_name  VARCHAR(64)  NOT NULL,
  phone         VARCHAR(20)  NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'active',
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS sys_role (
  id   BIGINT      NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);
