++ -- MySQL DDL for user core/profile/presence and simple id sequence for account numbers
-- Schema: user_core, user_profile, user_presence, account_sequence

-- 1) account_sequence: simple table to provide auto-increment numbers for account_no generation
CREATE TABLE IF NOT EXISTS account_sequence (
  id BIGINT NOT NULL AUTO_INCREMENT,
  prefix VARCHAR(16) DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) user_core: core stable fields
CREATE TABLE IF NOT EXISTS user_core (
  user_id BIGINT NOT NULL AUTO_INCREMENT,
  account_no VARCHAR(16) NOT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(256) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_account_no (account_no),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) user_profile: non-core mutable fields
CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT NOT NULL,
  nickname VARCHAR(64),
  avatar VARCHAR(255),
  sex TINYINT DEFAULT 0,
  active_status TINYINT DEFAULT 1,
  last_online_at DATETIME NULL,
  last_offline_at DATETIME NULL,
  last_login_ip VARCHAR(64) NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES user_core(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4) user_presence: optional high-frequency presence table
CREATE TABLE IF NOT EXISTS user_presence (
  user_id BIGINT NOT NULL,
  active_status TINYINT DEFAULT 0,
  last_active_at DATETIME NULL,
  last_ip VARCHAR(64) NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_presence_user FOREIGN KEY (user_id) REFERENCES user_core(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Minimal indexes: username/account_no already unique indexes

-- 5) Example initial data: create one test user
INSERT INTO account_sequence (prefix) VALUES ('IM');

INSERT INTO user_core (account_no, username, password_hash)
VALUES ('10000001', 'alice', '$2a$10$EXAMPLEHASHPLACEHOLDER0123456789abcdefghijkl');

INSERT INTO user_profile (user_id, nickname, avatar, sex, active_status)
VALUES (1, 'Alice', NULL, 0, 1);

