-- ============================================================
-- 售后工单 + Agent 审计事件表（可重复执行）
-- 用于「电商客服 + 售后 + 风控」多 Agent 场景
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `t_after_sale_ticket` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ticket_no`       VARCHAR(40)  NOT NULL                COMMENT '工单号',
  `user_id`         BIGINT       NOT NULL                COMMENT '用户 ID',
  `order_id`        BIGINT       NOT NULL                COMMENT '订单 ID',
  `intent`          VARCHAR(32)  DEFAULT NULL            COMMENT '意图：LOGISTICS/QUALITY/REFUND/MALICIOUS/OTHER',
  `status`          VARCHAR(24)  NOT NULL DEFAULT 'PENDING_REVIEW'
                    COMMENT 'PENDING_REVIEW/APPROVED/REJECTED/REFUNDED/CLOSED',
  `risk_level`      VARCHAR(16)  DEFAULT NULL            COMMENT 'LOW/MEDIUM/HIGH',
  `risk_score`      INT          DEFAULT 0               COMMENT '风控分 0-100',
  `decision`        VARCHAR(32)  DEFAULT NULL            COMMENT 'AUTO_APPROVE/MANUAL_REVIEW/REJECT',
  `decision_reason` VARCHAR(500) DEFAULT NULL            COMMENT '决策原因/命中规则',
  `refund_amount`   DECIMAL(10,2) DEFAULT NULL           COMMENT '退款金额',
  `thread_id`       VARCHAR(64)  DEFAULT NULL            COMMENT 'LangGraph thread_id，便于恢复',
  `reply`           TEXT         NULL                    COMMENT '给用户的回复',
  `assignee`        VARCHAR(50)  DEFAULT NULL            COMMENT '处理人',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`),
  KEY `idx_ast_user` (`user_id`),
  KEY `idx_ast_order` (`order_id`),
  KEY `idx_ast_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后工单';

CREATE TABLE IF NOT EXISTS `t_after_sale_event` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ticket_id`  BIGINT      NOT NULL                COMMENT '工单 ID',
  `step`       VARCHAR(40) NOT NULL                COMMENT '节点：INTENT/ORDER/RISK/POLICY/DECISION/REFUND/REPLY',
  `actor`      VARCHAR(50) DEFAULT 'agent'         COMMENT 'agent/system/admin',
  `payload`    TEXT        NULL                    COMMENT '节点输入输出 JSON（审计用）',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ase_ticket` (`ticket_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后工单 Agent 事件审计';
