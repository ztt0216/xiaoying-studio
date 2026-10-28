-- 小影素材工坊 数据库结构（MySQL 8.x）
SET NAMES utf8mb4;
-- 所有时间以 UTC 存储（连接串固定 connectionTimeZone=UTC）

CREATE DATABASE IF NOT EXISTS studio DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE studio;

CREATE TABLE IF NOT EXISTS app_user (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  nickname    VARCHAR(32) NOT NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_user_nickname (nickname)
) ENGINE=InnoDB;

-- 简单的不透明会话令牌：登录时签发，退出时删除
CREATE TABLE IF NOT EXISTS user_token (
  token       CHAR(36) PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_token_user (user_id),
  CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS template (
  id                   VARCHAR(64) PRIMARY KEY,
  title                VARCHAR(100) NOT NULL,
  category             VARCHAR(32) NOT NULL,
  cover_url            VARCHAR(255) NOT NULL,
  description          VARCHAR(500) NOT NULL,
  required_image_count INT NOT NULL,
  accepted_mime_types  VARCHAR(255) NOT NULL COMMENT '逗号分隔，如 image/jpeg,image/png',
  max_file_size_bytes  BIGINT NOT NULL,
  sort_order           INT NOT NULL DEFAULT 0,
  KEY idx_template_category (category, sort_order)
) ENGINE=InnoDB;

-- 用户上传的素材，文件落在后端本地目录 / 容器数据卷
CREATE TABLE IF NOT EXISTS asset (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id        BIGINT NOT NULL,
  template_id    VARCHAR(64) NOT NULL,
  original_name  VARCHAR(255) NOT NULL,
  mime_type      VARCHAR(64) NOT NULL,
  size_bytes     BIGINT NOT NULL,
  storage_path   VARCHAR(255) NOT NULL COMMENT '相对存储根目录的路径',
  url            VARCHAR(255) NOT NULL,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_asset_user (user_id),
  CONSTRAINT fk_asset_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 制作任务。status 为整体状态，stage 为当前/最后执行的阶段
CREATE TABLE IF NOT EXISTS task (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id        BIGINT NOT NULL,
  template_id    VARCHAR(64) NOT NULL,
  status         VARCHAR(16) NOT NULL COMMENT 'PENDING / PROCESSING / SUCCEEDED / FAILED',
  stage          VARCHAR(16) NULL COMMENT 'ASSET_CHECK / GENERATE / FINALIZE；PENDING 时为空',
  failed_stage   VARCHAR(16) NULL,
  error_message  VARCHAR(500) NULL,
  result_url     VARCHAR(255) NULL,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  started_at     DATETIME(3) NULL,
  finished_at    DATETIME(3) NULL,
  KEY idx_task_user_created (user_id, id),
  KEY idx_task_status (status),
  CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_task_template FOREIGN KEY (template_id) REFERENCES template (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS task_asset (
  task_id     BIGINT NOT NULL,
  slot_index  INT NOT NULL,
  asset_id    BIGINT NOT NULL,
  PRIMARY KEY (task_id, slot_index),
  CONSTRAINT fk_ta_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE,
  CONSTRAINT fk_ta_asset FOREIGN KEY (asset_id) REFERENCES asset (id)
) ENGINE=InnoDB;

-- 每个阶段一条执行记录，便于观察独立的执行边界
CREATE TABLE IF NOT EXISTS task_step (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id      BIGINT NOT NULL,
  stage        VARCHAR(16) NOT NULL,
  status       VARCHAR(16) NOT NULL COMMENT 'RUNNING / SUCCEEDED / FAILED',
  message      VARCHAR(500) NULL,
  started_at   DATETIME(3) NOT NULL,
  finished_at  DATETIME(3) NULL,
  KEY idx_step_task (task_id, id),
  CONSTRAINT fk_step_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE
) ENGINE=InnoDB;
