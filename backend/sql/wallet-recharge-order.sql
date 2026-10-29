-- =============================================================================
-- 钱包充值订单表（支付宝沙箱 PC 网页支付）
-- 幂等保证：付款确认时 UPDATE status='PAID' WHERE status='PENDING'，
--           命中(affected=1)才加余额，重复查询/重复通知不会重复入账。
-- =============================================================================

SET NAMES utf8mb4;
DROP TABLE IF EXISTS t_wallet_recharge_order;

CREATE TABLE t_wallet_recharge_order (
  id            BIGINT         NOT NULL AUTO_INCREMENT       COMMENT '主键',
  out_trade_no  VARCHAR(64)    NOT NULL                       COMMENT '商户订单号（本系统生成）',
  user_id       BIGINT         NOT NULL                       COMMENT '充值用户 ID',
  amount        DECIMAL(10, 2) NOT NULL                       COMMENT '充值金额（元）',
  status        VARCHAR(20)    NOT NULL DEFAULT 'PENDING'     COMMENT 'PENDING 待付 / PAID 已付 / CLOSED 已关闭',
  trade_no      VARCHAR(64)    DEFAULT NULL                   COMMENT '支付宝交易号（付款后回填）',
  created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_out_trade_no (out_trade_no),
  KEY idx_user (user_id),
  CONSTRAINT fk_wro_user FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包充值订单（支付宝沙箱）';
