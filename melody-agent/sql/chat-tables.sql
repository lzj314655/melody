-- 麦乐迪商城 · 智能助手聊天记录表
-- 存放于商城同一 melody_shop 库，便于后续管理端查看客服记录
-- 幂等：可重复执行

SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `t_chat_session` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id` VARCHAR(64)  NOT NULL COMMENT '前端持有的会话标识(UUID)',
  `user_id`    BIGINT       NULL     COMMENT '登录用户ID，从JWT解析；未登录为NULL',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='智能助手会话';

CREATE TABLE IF NOT EXISTS `t_chat_message` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id`  VARCHAR(64)  NOT NULL COMMENT '关联 t_chat_session.session_id',
  `role`        VARCHAR(16)  NOT NULL COMMENT 'user / assistant / tool',
  `content`     TEXT         NULL     COMMENT '消息文本',
  `tool_name`   VARCHAR(64)  NULL     COMMENT 'role=tool 时的工具名',
  `tool_args`   TEXT         NULL     COMMENT '工具入参JSON',
  `tool_result` TEXT         NULL     COMMENT '工具结果JSON(截断)',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='智能助手消息明细(含工具调用链审计)';
