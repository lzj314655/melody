-- 修正演示账号密码：data-init.sql 中的 BCrypt hash 与真实可用密码不一致
-- 用户 hash 对应明文 "password"（13800138001/2/3）；管理员 hash 对应 admin/admin123、operator/operator123
USE melody_shop;
UPDATE t_user SET password = '$2a$10$l7bAJ3sYGyeINCEkI.09q.1mcBYr9Zbr0TG6NUb54MjIIdDzMx7mK'
WHERE phone IN ('13800138001', '13800138002', '13800138003');

UPDATE t_admin SET password = '$2a$10$VpAkUVi4AP./rrEE4EgpsejDqVWDqqzahGvpzfLWqVvIwa1FIkr/G'
WHERE username = 'admin';
UPDATE t_admin SET password = '$2a$10$YfCtrgt.95Im0rC11tzWEulqj0NpOFRvxjc6QLFTG2V0XNWWd8KMO'
WHERE username = 'operator';
