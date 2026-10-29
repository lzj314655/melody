-- =====================================================
-- 一次性清理：删除同一用户对同一 coupon 的多余"可用"行
-- 保留最旧一条；多余的"可用"会让用户能无限领取（绕过库存）
-- =====================================================

USE melody_shop;

-- 1) 查看现有重复可用券
SELECT user_id, coupon_id, COUNT(*) AS cnt
FROM t_user_coupon
WHERE status = '可用'
GROUP BY user_id, coupon_id
HAVING cnt > 1;

-- 2) 删除多余行：只保留每个 (user_id, coupon_id, status='可用') 分组中 id 最小的一条
DELETE uc FROM t_user_coupon uc
JOIN (
  SELECT MIN(id) AS keep_id, user_id, coupon_id
  FROM t_user_coupon
  WHERE status = '可用'
  GROUP BY user_id, coupon_id
  HAVING COUNT(*) > 1
) dups ON uc.user_id = dups.user_id
     AND uc.coupon_id = dups.coupon_id
     AND uc.status = '可用'
     AND uc.id > dups.keep_id;

-- 3) 校验：现在每个 (user_id, coupon_id, status='可用') 最多 1 条
SELECT user_id, coupon_id, COUNT(*) AS cnt
FROM t_user_coupon
WHERE status = '可用'
GROUP BY user_id, coupon_id
HAVING cnt > 1;

-- 4) 看看是否还有"已用"的剩余待清理
SELECT 'after cleanup' AS step, COUNT(*) AS active_remaining FROM t_user_coupon WHERE status = '可用';