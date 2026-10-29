-- =============================================================================
-- 订单表加列：支付方式 + 支付宝交易号（订单支付宝沙箱支付）
-- 容器全新初始化时 schema.sql 已含部分列/索引，故全部改为条件式幂等执行
-- =============================================================================

-- 1) t_order 加 pay_method / trade_no 列（已存在则跳过）
SET @has_pay_method = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_order' AND column_name = 'pay_method');
SET @ddl = IF(@has_pay_method = 0,
  'ALTER TABLE t_order ADD COLUMN pay_method VARCHAR(20) DEFAULT NULL COMMENT ''支付方式：WALLET 余额 / ALIPAY 支付宝沙箱'' AFTER status, ADD COLUMN trade_no VARCHAR(64) DEFAULT NULL COMMENT ''支付宝交易号（付款确认回填）'' AFTER pay_method',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 清理历史重复领取:每个 (user_id, coupon_id) 只保留一条
DELETE uc FROM t_user_coupon uc
JOIN t_user_coupon dup ON uc.user_id = dup.user_id AND uc.coupon_id = dup.coupon_id AND dup.id < uc.id;

DELETE FROM t_user_coupon
WHERE id NOT IN (
    SELECT keep_id FROM (
                            SELECT MIN(id) AS keep_id FROM t_user_coupon GROUP BY user_id, coupon_id
                        ) t
);

-- 3) 加唯一索引(终身一次)——已存在则跳过
SET @has_uk = (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_user_coupon' AND index_name = 'uk_user_coupon');
SET @ddl = IF(@has_uk = 0,
  'ALTER TABLE t_user_coupon ADD UNIQUE KEY uk_user_coupon (user_id, coupon_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
