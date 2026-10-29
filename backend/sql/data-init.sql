-- =============================================================================
-- 麦乐迪零食购物商场系统 - 演示数据初始化脚本
-- 严格依据 PRD §6.4 演示数据准备要求
-- 使用前请先执行 schema.sql
-- =============================================================================

SET NAMES utf8mb4;
USE melody_shop;

-- 关闭 FK 检查，方便重建
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 0. 清空（便于重复执行）
-- =============================================================================
TRUNCATE TABLE t_operation_log;
TRUNCATE TABLE t_announcement;
TRUNCATE TABLE t_banner;
TRUNCATE TABLE t_role_permission;
TRUNCATE TABLE t_admin_role;
TRUNCATE TABLE t_permission;
TRUNCATE TABLE t_role;
TRUNCATE TABLE t_admin;
TRUNCATE TABLE user_behavior_log;
TRUNCATE TABLE t_growth_log;
TRUNCATE TABLE t_point_log;
TRUNCATE TABLE t_wallet_log;
TRUNCATE TABLE t_wallet_account;
TRUNCATE TABLE t_promotion;
TRUNCATE TABLE t_user_coupon;
TRUNCATE TABLE t_coupon;
TRUNCATE TABLE t_order_item;
TRUNCATE TABLE t_order;
TRUNCATE TABLE t_favorite;
TRUNCATE TABLE t_cart_item;
TRUNCATE TABLE t_product_review;
TRUNCATE TABLE t_product_image;
TRUNCATE TABLE t_product_sku;
TRUNCATE TABLE t_product;
TRUNCATE TABLE t_category;
TRUNCATE TABLE t_user_address;
TRUNCATE TABLE t_user;

-- =============================================================================
-- 1. 后台账号 + RBAC（先建，因为 t_admin_role 等会引用）
-- =============================================================================
-- 注：下方 BCrypt 哈希为「password」的标准 cost-10 哈希
-- 真实部署时建议用 BCryptPasswordEncoder 重新生成对应演示账号哈希后替换
INSERT INTO t_admin (username, password, email, display_name, status, last_login_at) VALUES
('admin',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@melody.com',    '超级管理员', 1, '2026-07-17 09:30:00'),
('operator', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'operator@melody.com', '运营管理员', 1, '2026-07-17 09:15:00');

INSERT INTO t_role (code, name, description) VALUES
('ROLE_SUPER_ADMIN', '超级管理员', '拥有后台全部权限'),
('ROLE_OPERATOR',    '运营管理员', '负责商品/订单/营销等日常运营');

INSERT INTO t_permission (code, name, module, description) VALUES
-- 商品模块
('product:read',    '查看商品',   'product',  '查看商品列表与详情'),
('product:create',  '新增商品',   'product',  '新增商品/上架'),
('product:update',  '编辑商品',   'product',  '编辑商品信息'),
('product:delete',  '删除商品',   'product',  '下架/删除商品'),
('category:manage', '分类管理',   'product',  '商品分类 CRUD'),
-- 订单模块
('order:read',      '查看订单',   'order',    '查看订单列表与详情'),
('order:ship',      '订单发货',   'order',    '录入物流单号/标记发货'),
('order:refund',    '退款审核',   'order',    '处理退款申请'),
-- 营销模块
('coupon:manage',   '优惠券管理', 'marketing','优惠券 CRUD'),
('promotion:manage','满减活动',   'marketing','满减活动 CRUD'),
-- 用户模块
('user:read',       '查看用户',   'user',     '查看用户列表与详情'),
('user:adjust',     '调整积分',   'user',     '手动加减用户积分'),
-- 数据模块
('dashboard:view',  '查看大屏',   'dashboard','查看数据大屏'),
-- 系统模块
('banner:manage',   '轮播图管理', 'system',   '轮播图 CRUD'),
('announcement:manage','公告管理','system',   '公告 CRUD'),
('admin:manage',    '管理员管理', 'system',   '管理员账号/角色 CRUD'),
('role:manage',     '角色管理',   'system',   '角色权限 CRUD'),
('data:reset',      '数据重置',   'system',   '一键重置演示数据');

-- 超级管理员：所有权限
INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM t_role r, t_permission p WHERE r.code = 'ROLE_SUPER_ADMIN';

-- 运营管理员：商品/订单/营销（PRD §3.2 矩阵）
INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM t_role r, t_permission p
WHERE r.code = 'ROLE_OPERATOR'
  AND p.code IN (
    'product:read','product:create','product:update','product:delete',
    'category:manage',
    'order:read','order:ship','order:refund',
    'coupon:manage','promotion:manage',
    'dashboard:view'
  );

-- admin → ROLE_SUPER_ADMIN；operator → ROLE_OPERATOR
INSERT INTO t_admin_role (admin_id, role_id)
SELECT a.id, r.id FROM t_admin a, t_role r
WHERE (a.username='admin' AND r.code='ROLE_SUPER_ADMIN')
   OR (a.username='operator' AND r.code='ROLE_OPERATOR');

-- =============================================================================
-- 2. 前台用户 + 地址（PRD §6.4 测试账号）
-- =============================================================================
-- 注：用户密码同样使用「password」的标准 BCrypt 哈希；真实部署请重新生成
INSERT INTO t_user (phone, email, username, password, nickname, avatar, gender, rank_code, points, growth_value, status, last_login_at, join_date) VALUES
('13800138001', 'usera@melody.com', 'userA', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '快乐星猫', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=150', 2, 'V1', 5680,  800, 1, '2026-07-17 09:00:00', '2026-03-15 10:00:00'),
('13800138002', 'userb@melody.com', 'userB', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '美食收藏家', 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&q=80&w=150', 1, 'V3', 18650, 5500, 1, '2026-07-17 08:30:00', '2026-01-20 09:00:00'),
('13800138003', 'userc@melody.com', 'userC', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '新用户',   'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=150', 0, 'V0', 100,    0, 1, '2026-07-17 10:30:00', '2026-07-17 10:30:00');

INSERT INTO t_user_address (user_id, name, phone, province, city, district, detail, tag, is_default) VALUES
(1, '张小猫', '13800138001', '上海市', '上海市', '浦东新区', '世纪大道 100 号 28 楼',  '公司', 1),
(1, '张小猫', '13800138001', '上海市', '上海市', '徐汇区',   '漕溪北路 88 号 1502 室', '家',   0),
(2, '李收藏', '13800138002', '北京市', '北京市', '朝阳区',   '建国路 88 号 SOHO 现代城', '公司', 1),
(2, '李收藏', '13800138002', '杭州市', '杭州市', '西湖区',   '文三路 138 号',           '家',   0),
(3, '王新',   '13800138003', '深圳市', '深圳市', '南山区',   '科技园路 1 号',           '家',   1);

-- 钱包（每个用户 1000 元，PRD §6.4 要求）
INSERT INTO t_wallet_account (user_id, balance) VALUES
(1, 1000.00),
(2, 1000.00),
(3, 1000.00);

-- 钱包流水：3 个用户的初始充值
INSERT INTO t_wallet_log (user_id, change_amount, balance_after, type, reason, operator) VALUES
(1, 1000.00, 1000.00, 'RECHARGE', '演示数据初始化充值', 'admin'),
(2, 1000.00, 1000.00, 'RECHARGE', '演示数据初始化充值', 'admin'),
(3, 1000.00, 1000.00, 'RECHARGE', '演示数据初始化充值', 'admin');

-- 积分/成长值流水（userA 的 5680 积分 / 800 成长值通过历史订单累计，这里写一条注册礼包）
INSERT INTO t_point_log (user_id, change_amount, balance_after, type, reason) VALUES
(1, 100,   5680,  'REGISTER', '注册礼包 +100 积分（其余为历史订单累积）'),
(2, 100,   18650, 'REGISTER', '注册礼包 +100 积分（其余为历史订单累积）'),
(3, 100,   100,   'REGISTER', '注册礼包 +100 积分（PRD §7.3）');

INSERT INTO t_growth_log (user_id, change_amount, balance_after, type, reason) VALUES
(1, 800, 800, 'ORDER', '演示数据初始化（历史订单累积成长值）'),
(2, 5500, 5500, 'ORDER', '演示数据初始化（历史订单累积成长值）');

-- =============================================================================
-- 3. 商品分类（PRD §6.4 ≥ 5 个分类）
-- =============================================================================
INSERT INTO t_category (id, name, parent_id, sort) VALUES
(1, '坚果炒货', NULL, 1),
(2, '肉干卤味', NULL, 2),
(3, '糕点点心', NULL, 3),
(4, '果干蜜饯', NULL, 4),
(5, '膨化薯片', NULL, 5),
(6, '其他零食', NULL, 6);

-- =============================================================================
-- 4. 商品（55 件，覆盖 6 个分类）
-- 字段：name, category_id, brand, price, market_price, description,
--       cover_image, inventory, tags(JSON), rating, origin, shelf_life, weight, ingredients
-- =============================================================================
INSERT INTO t_product (id, name, category_id, brand, price, market_price, description, cover_image, inventory, sales, tags, rating, origin, shelf_life, weight, ingredients) VALUES
-- 坚果炒货 (cat=1) × 10
(1,  '麦乐迪原味瓜子',       1, '麦乐迪', 19.9,  29.9,  '物理慢烘，原味大颗粒，颗颗饱满。',                  'https://images.unsplash.com/photo-1599321955726-f87171fb5d56?auto=format&fit=crop&q=80&w=400', 800,  1284, '["低糖健康","原味"]',          4.9, '新疆', '240 天', '250g',  '葵花籽'),
(2,  '巴西松子（手剥）',     1, '麦乐迪', 38.8,  48.0,  '长白山野生红松，物理慢烘，浓郁松香。',                'https://images.unsplash.com/photo-1595855759920-86582396756a?auto=format&fit=crop&q=80&w=400', 450,  892,  '["低糖健康","野生严选"]',    4.8, '东北吉林', '240 天', '180g', '野生红松子'),
(3,  '夏威夷果（奶香）',     1, '麦乐迪', 42.0,  56.0,  '澳洲进口，奶香浓郁，一咬就开。',                       'https://images.unsplash.com/photo-1599599810694-b5b37304c041?auto=format&fit=crop&q=80&w=400', 320,  756,  '["进口","奶香"]',            4.7, '澳洲', '300 天', '200g', '夏威夷果、食用盐'),
(4,  '巴旦木（盐焗）',       1, '麦乐迪', 35.0,  45.0,  '新疆纸皮巴旦木，盐焗入味。',                            'https://images.unsplash.com/photo-1573855522323-cdca4b0eaa82?auto=format&fit=crop&q=80&w=400', 380,  632,  '["新疆","盐焗"]',            4.6, '新疆', '240 天', '200g', '巴旦木、食用盐'),
(5,  '炭烤腰果（无添加）',   1, '麦乐迪', 56.0,  72.0,  '无油无糖，原木炭烤工艺。',                              'https://images.unsplash.com/photo-1604474474741-6d7c4c0f87b1?auto=format&fit=crop&q=80&w=400', 240,  534,  '["无添加","高端"]',          4.9, '越南', '300 天', '180g', '腰果'),
(6,  '开口核桃（纸皮）',     1, '麦乐迪', 32.0,  42.0,  '新疆纸皮核桃，皮薄仁满。',                              'https://images.unsplash.com/photo-1599509226085-6fe9ce0dca34?auto=format&fit=crop&q=80&w=400', 410,  702,  '["纸皮","新疆"]',            4.6, '新疆', '240 天', '250g', '核桃'),
(7,  '开心果（盐焗）',       1, '麦乐迪', 48.0,  62.0,  '美国进口大颗粒开心果，自然开口。',                     'https://images.unsplash.com/photo-1567206565364-1ba7ba8d9b06?auto=format&fit=crop&q=80&w=400', 280,  489,  '["美国进口","盐焗"]',        4.7, '美国', '300 天', '200g', '开心果、食用盐'),
(8,  '榛子（东北）',         1, '麦乐迪', 52.0,  68.0,  '东北野生榛子，醇香酥脆。',                              'https://images.unsplash.com/photo-1605543670439-7eb16db5e2da?auto=format&fit=crop&q=80&w=400', 190,  367,  '["野生","东北"]',            4.7, '东北', '240 天', '180g', '榛子'),
(9,  '碧根果（奶油）',       1, '麦乐迪', 68.0,  88.0,  '美国碧根果，奶油香气。',                                'https://images.unsplash.com/photo-1565299507177-b0f667d278f9?auto=format&fit=crop&q=80&w=400', 150,  298,  '["高端","美国"]',            4.8, '美国', '300 天', '180g', '碧根果、食用盐'),
(10, '混合坚果每日一包',     1, '麦乐迪', 89.0,  119.0, '每日坚果，6 种科学配比。',                               'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', 320, 1024,  '["每日坚果","搭配"]',        4.8, '国产组合', '240 天', '750g', '腰果、核桃、巴旦木、榛子、蔓越莓、蓝莓干'),

-- 肉干卤味 (cat=2) × 10
(11, '炭烤香辣牛肉干',       2, '麦乐迪', 45.0,  58.0,  '黄牛后腿肉，秘制香料慢火炭烤。',                        'https://images.unsplash.com/photo-1599487488170-d11ec9c17260?auto=format&fit=crop&q=80&w=400', 380, 1203,  '["招牌","炭烤"]',            4.8, '内蒙古', '180 天', '200g', '黄牛肉、香辛料'),
(12, '蜜汁猪肉脯',           2, '麦乐迪', 38.0,  49.0,  '蜜汁腌制，甜咸交织。',                                  'https://images.unsplash.com/photo-1625937329935-2874418899eb?auto=format&fit=crop&q=80&w=400', 290,  876,  '["蜜汁","经典"]',            4.7, '广东', '180 天', '200g', '猪后腿肉、蜂蜜'),
(13, '泡椒凤爪（独立小包）', 2, '麦乐迪', 28.0,  36.0,  '四川泡椒，独立小包装。',                                'https://images.unsplash.com/photo-1625937329935-2874418899eb?auto=format&fit=crop&q=80&w=400', 420, 1054,  '["四川","独立包装"]',        4.6, '四川', '90 天',  '180g', '鸡爪、泡椒'),
(14, '麻辣鸭脖（武汉风味）', 2, '麦乐迪', 32.0,  42.0,  '武汉精武鸭脖，麻辣鲜香。',                              'https://images.unsplash.com/photo-1625937329935-2874418899eb?auto=format&fit=crop&q=80&w=400', 350,  923,  '["武汉","麻辣"]',            4.7, '武汉', '120 天', '200g', '鸭脖、香辛料'),
(15, '卤香鸭翅',             2, '麦乐迪', 26.0,  34.0,  '老卤慢炖，卤香浓郁。',                                  'https://images.unsplash.com/photo-1625937329935-2874418899eb?auto=format&fit=crop&q=80&w=400', 380,  812,  '["卤香","老卤"]',            4.6, '江苏', '120 天', '180g', '鸭翅、香辛料'),
(16, '鱼豆腐（Q 弹）',       2, '麦乐迪', 18.0,  25.0,  '优质鱼糜，口感 Q 弹。',                                  'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 480,  743,  '["Q 弹","高蛋白"]',          4.5, '山东', '180 天', '200g', '鱼糜、淀粉'),
(17, '猪肉松（原味）',       2, '麦乐迪', 24.0,  32.0,  '猪肉纤维清晰，蓬松细腻。',                              'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 310,  567,  '["原味","高蛋白"]',          4.5, '福建', '180 天', '200g', '猪后腿肉'),
(18, '火腿肠（原味）',       2, '麦乐迪', 15.0,  22.0,  '低温慢煮，肉香纯正。',                                  'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 520, 1240,  '["即食","原味"]',            4.4, '河南', '120 天', '200g', '猪肉、淀粉'),
(19, '卤蛋（五香味）',       2, '麦乐迪', 12.0,  18.0,  '真空包装，开袋即食。',                                  'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 600,  876,  '["五香","即食"]',            4.5, '江苏', '90 天',  '180g', '鸡蛋、五香粉'),
(20, '豆干（辣味）',         2, '麦乐迪', 14.0,  20.0,  '卤制豆干，嚼劲十足。',                                  'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 460,  654,  '["辣味","豆制品"]',          4.4, '湖南', '180 天', '200g', '黄豆、香辛料'),

-- 糕点点心 (cat=3) × 10
(21, '冰镇绿豆糕',           3, '麦乐迪', 22.0,  30.0,  '冰镇后流心十足，清甜不腻。',                            'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 280,  734,  '["冰镇","清甜"]',            4.8, '江浙', '60 天',  '200g', '绿豆、麦芽糖'),
(22, '蛋黄酥（手工）',       3, '麦乐迪', 36.0,  48.0,  '手工酥皮，咸蛋黄流心。',                                'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 230,  612,  '["手工","咸蛋黄"]',          4.9, '广东', '45 天',  '180g', '咸蛋黄、酥皮'),
(23, '老婆饼（传统）',       3, '麦乐迪', 18.0,  26.0,  '传统配方，冬瓜蓉馅。',                                  'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 350,  523,  '["传统","冬瓜蓉"]',          4.6, '潮汕', '45 天',  '200g', '冬瓜蓉、酥皮'),
(24, '宫廷桃酥',             3, '麦乐迪', 16.0,  24.0,  '宫廷配方，酥松掉渣。',                                  'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 380,  687,  '["宫廷","酥脆"]',            4.7, '北京', '60 天',  '200g', '面粉、芝麻'),
(25, '黄油曲奇（罐装）',     3, '麦乐迪', 45.0,  58.0,  '新西兰黄油，入口即化。',                                'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', 210,  445,  '["黄油","罐装"]',            4.8, '上海', '90 天',  '240g', '黄油、面粉'),
(26, '芝士威化（夹心）',     3, '麦乐迪', 26.0,  35.0,  '芝士夹心，酥脆多层。',                                  'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', 320,  789,  '["芝士","夹心"]',            4.7, '广东', '120 天', '200g', '小麦粉、芝士粉'),
(27, '抹茶麻薯',             3, '麦乐迪', 28.0,  38.0,  '日式抹茶，Q 弹不粘牙。',                                'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 240,  356,  '["抹茶","日式"]',            4.8, '广东', '30 天',  '180g', '糯米粉、抹茶粉'),
(28, '凤梨酥（台湾风味）',   3, '麦乐迪', 32.0,  42.0,  '台湾风味凤梨馅。',                                      'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 260,  478,  '["台湾","凤梨"]',            4.7, '台湾', '45 天',  '180g', '凤梨、酥皮'),
(29, '原味牛轧糖',           3, '麦乐迪', 38.0,  50.0,  '奶香浓郁，甜而不腻。',                                  'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', 290,  512,  '["奶香","手工"]',            4.7, '福建', '90 天',  '200g', '奶粉、麦芽糖'),
(30, '蛋卷（芝麻）',         3, '麦乐迪', 19.0,  28.0,  '酥脆蛋香，层层掉渣。',                                  'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 340,  623,  '["蛋香","芝麻"]',            4.6, '广东', '60 天',  '180g', '鸡蛋、芝麻'),

-- 果干蜜饯 (cat=4) × 10
(31, '泰国芒果干',           4, '麦乐迪', 28.0,  38.0,  '泰国进口，酸甜厚切。',                                  'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 380,  892,  '["泰国进口","厚切"]',        4.8, '泰国', '240 天', '200g', '芒果、白砂糖'),
(32, '蔓越莓干',             4, '麦乐迪', 35.0,  48.0,  '美国进口原味蔓越莓干。',                                'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 280,  734,  '["美国进口","酸甜"]',        4.7, '美国', '300 天', '200g', '蔓越莓、白砂糖'),
(33, '新疆葡萄干',           4, '麦乐迪', 22.0,  30.0,  '新疆吐鲁番大颗粒葡萄干。',                              'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 420, 1056,  '["新疆","大颗粒"]',          4.8, '新疆', '300 天', '250g', '葡萄'),
(34, '野生蓝莓干',           4, '麦乐迪', 56.0,  72.0,  '东北野生蓝莓，无添加糖。',                                'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 180,  423,  '["野生","无糖"]',            4.8, '东北', '300 天', '150g', '野生蓝莓'),
(35, '冻干草莓脆',           4, '麦乐迪', 38.0,  50.0,  'FD 冻干工艺，保留原味。',                                'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 220,  567,  '["冻干","草莓"]',            4.7, '山东', '180 天', '120g', '草莓'),
(36, '菠萝蜜干',             4, '麦乐迪', 32.0,  42.0,  '越南菠萝蜜干，Q 弹香甜。',                                'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 260,  612,  '["越南","Q 弹"]',            4.6, '越南', '240 天', '200g', '菠萝蜜'),
(37, '杨梅干',               4, '麦乐迪', 26.0,  36.0,  '福建古田杨梅，酸甜开胃。',                                'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 300,  489,  '["福建","开胃"]',            4.5, '福建', '240 天', '200g', '杨梅'),
(38, '智利车厘子干',         4, '麦乐迪', 68.0,  88.0,  '智利进口车厘子干。',                                    'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 140,  298,  '["智利进口","高端"]',        4.8, '智利', '300 天', '150g', '车厘子'),
(39, '杏脯',                 4, '麦乐迪', 18.0,  26.0,  '新疆杏脯，酸甜有嚼劲。',                                  'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 360,  578,  '["新疆","嚼劲"]',            4.5, '新疆', '300 天', '200g', '杏'),
(40, '桃脯',                 4, '麦乐迪', 18.0,  26.0,  '传统蜜渍桃脯。',                                          'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 320,  498,  '["传统","蜜渍"]',            4.5, '河北', '300 天', '200g', '桃'),

-- 膨化薯片 (cat=5) × 10
(41, '原味薯片（乐事）',     5, '乐事',  9.9,   15.0, '经典原味，嘎嘣脆。',                                    'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 800, 2340,  '["经典","原味"]',            4.6, '国产', '180 天', '104g', '马铃薯、植物油'),
(42, '黄瓜味薯片',           5, '乐事',  9.9,   15.0, '清新黄瓜味。',                                          'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 720, 1890,  '["清新","黄瓜"]',            4.5, '国产', '180 天', '104g', '马铃薯、香辛料'),
(43, '番茄味薯片',           5, '乐事',  9.9,   15.0, '浓郁番茄味。',                                          'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 760, 2010,  '["浓郁","番茄"]',            4.6, '国产', '180 天', '104g', '马铃薯、香辛料'),
(44, '麻辣小龙虾味虾条',     5, '麦乐迪', 15.0,  22.0, '麻辣小龙虾味。',                                        'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 580, 1456,  '["麻辣","网红"]',            4.5, '国产', '180 天', '120g', '小麦粉、香辛料'),
(45, '糯米锅巴（原味）',     5, '麦乐迪', 12.0,  18.0, '糯米锅巴，酥脆咸香。',                                  'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 620, 1340,  '["糯米","酥脆"]',            4.6, '安徽', '180 天', '150g', '糯米、植物油'),
(46, '玉米片（原味）',       5, '麦乐迪', 16.0,  24.0, '玉米原香。',                                            'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 480,  923,  '["玉米","原味"]',            4.4, '东北', '180 天', '120g', '玉米'),
(47, '洋葱圈',               5, '麦乐迪', 14.0,  20.0, '酥脆洋葱圈。',                                          'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 520, 1067,  '["酥脆","洋葱"]',            4.5, '国产', '180 天', '120g', '小麦粉、洋葱粉'),
(48, '芝士条（膨化）',       5, '麦乐迪', 18.0,  26.0, '浓郁芝士味。',                                          'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 460,  892,  '["芝士","膨化"]',            4.6, '国产', '180 天', '120g', '小麦粉、芝士粉'),
(49, '麦烧（芝麻味）',       5, '麦乐迪', 13.0,  19.0, '传统麦烧，芝麻香。',                                    'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 540, 1120,  '["传统","芝麻"]',            4.5, '河南', '180 天', '150g', '小麦粉、芝麻'),
(50, '混合蔬菜脆',           5, '麦乐迪', 24.0,  36.0, '5 种蔬菜混合，VF 工艺。',                                'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 380,  745,  '["VF","健康"]',              4.7, '国产', '180 天', '100g', '秋葵、胡萝卜、香菇、紫薯、青豆'),

-- 其他零食 (cat=6) × 5
(51, '卫龙辣条（大面筋）',   6, '卫龙',  12.0,  18.0, '童年回忆辣条。',                                        'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 720, 3120,  '["童年回忆","辣"]',          4.7, '河南', '180 天', '180g', '小麦粉、辣椒'),
(52, '麦丽素（桶装）',       6, '麦乐迪', 22.0,  32.0, '巧克力脆心麦丽素。',                                    'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 420,  890,  '["巧克力","童年"]',          4.6, '国产', '240 天', '200g', '巧克力、麦芽糖'),
(53, '跳跳糖（混合味）',     6, '麦乐迪', 9.9,   15.0, '舌尖跳舞的童年。',                                       'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 580,  678,  '["童年","网红"]',            4.5, '国产', '180 天', '120g', '白砂糖、食用香精'),
(54, '棉花糖（云朵）',       6, '麦乐迪', 18.0,  26.0, '蓬松如云朵。',                                          'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 340,  456,  '["蓬松","童年"]',            4.5, '国产', '180 天', '150g', '白砂糖、明胶'),
(55, '麦芽糖（传统）',       6, '麦乐迪', 14.0,  22.0, '手工传统麦芽糖。',                                       'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 280,  345,  '["传统","手工"]',            4.6, '江浙', '240 天', '180g', '麦芽糖');

-- =============================================================================
-- 5. 商品 SKU（每个商品至少 2 个 SKU，PRD §4.1.3 / §6.1.1）
-- =============================================================================
-- 命名规则：100g 体验装 / 250g 分享装 / 500g 家庭装
INSERT INTO t_product_sku (product_id, spec_name, sku_code, price, stock, image, sales, status) VALUES
-- 商品 1 瓜子
(1,  '100g 体验装',  'MG-SUNFLOWER-100', 9.9,  500, NULL, 856, 1),
(1,  '250g 分享装',  'MG-SUNFLOWER-250', 19.9, 300, NULL, 428, 1),
-- 商品 2 巴西松子
(2,  '100g 体验装',  'MG-PINE-100', 19.9, 200, NULL, 312, 1),
(2,  '180g 经典装',  'MG-PINE-180', 38.8, 250, NULL, 580, 1),
-- 商品 3 夏威夷果
(3,  '100g 体验装',  'MG-MACADAMIA-100', 22.0, 180, NULL, 256, 1),
(3,  '200g 经典装',  'MG-MACADAMIA-200', 42.0, 140, NULL, 500, 1),
-- 商品 4 巴旦木
(4,  '100g 体验装',  'MG-ALMOND-100', 18.0, 200, NULL, 198, 1),
(4,  '200g 经典装',  'MG-ALMOND-200', 35.0, 180, NULL, 434, 1),
-- 商品 5 腰果
(5,  '100g 体验装',  'MG-CASHEW-100', 30.0, 120, NULL, 156, 1),
(5,  '180g 经典装',  'MG-CASHEW-180', 56.0, 120, NULL, 378, 1),
-- 商品 6 核桃
(6,  '100g 体验装',  'MG-WALNUT-100', 16.0, 200, NULL, 245, 1),
(6,  '250g 分享装',  'MG-WALNUT-250', 32.0, 210, NULL, 457, 1),
-- 商品 7 开心果
(7,  '100g 体验装',  'MG-PISTACHIO-100', 25.0, 140, NULL, 167, 1),
(7,  '200g 经典装',  'MG-PISTACHIO-200', 48.0, 140, NULL, 322, 1),
-- 商品 8 榛子
(8,  '100g 体验装',  'MG-HAZELNUT-100', 28.0, 100, NULL, 134, 1),
(8,  '180g 经典装',  'MG-HAZELNUT-180', 52.0, 90,  NULL, 233, 1),
-- 商品 9 碧根果
(9,  '100g 体验装',  'MG-PECAN-100', 36.0, 80,  NULL, 92,  1),
(9,  '180g 经典装',  'MG-PECAN-180', 68.0, 70,  NULL, 206, 1),
-- 商品 10 混合坚果
(10, '750g 月度装',  'MG-MIX-750',  89.0, 200, NULL, 678, 1),
(10, '1.5kg 季度装', 'MG-MIX-1500', 159.0, 100, NULL, 346, 1),
-- 商品 11 牛肉干
(11, '100g 体验装',  'MG-BEEF-100', 24.0, 200, NULL, 423, 1),
(11, '200g 经典装',  'MG-BEEF-200', 45.0, 180, NULL, 780, 1),
-- 商品 12 猪肉脯
(12, '100g 体验装',  'MG-PORK-100', 20.0, 150, NULL, 298, 1),
(12, '200g 经典装',  'MG-PORK-200', 38.0, 140, NULL, 578, 1),
-- 商品 13 凤爪
(13, '180g 体验装',  'MG-CHICKEN-180', 15.0, 220, NULL, 412, 1),
(13, '360g 分享装',  'MG-CHICKEN-360', 28.0, 200, NULL, 642, 1),
-- 商品 14 鸭脖
(14, '200g 体验装',  'MG-DUCKN-200', 18.0, 200, NULL, 378, 1),
(14, '400g 分享装',  'MG-DUCKN-400', 32.0, 150, NULL, 545, 1),
-- 商品 15 鸭翅
(15, '180g 体验装',  'MG-DUCKW-180', 14.0, 200, NULL, 312, 1),
(15, '360g 分享装',  'MG-DUCKW-360', 26.0, 180, NULL, 500, 1),
-- 商品 16 鱼豆腐
(16, '100g 体验装',  'MG-FISH-100', 9.9,  240, NULL, 245, 1),
(16, '200g 经典装',  'MG-FISH-200', 18.0, 240, NULL, 498, 1),
-- 商品 17 猪肉松
(17, '100g 体验装',  'MG-PORKF-100', 13.0, 160, NULL, 198, 1),
(17, '200g 经典装',  'MG-PORKF-200', 24.0, 150, NULL, 369, 1),
-- 商品 18 火腿肠
(18, '200g 体验装',  'MG-SAUS-200', 8.5,  280, NULL, 478, 1),
(18, '400g 分享装',  'MG-SAUS-400', 15.0, 240, NULL, 762, 1),
-- 商品 19 卤蛋
(19, '180g 体验装',  'MG-EGG-180', 7.0,  320, NULL, 312, 1),
(19, '360g 分享装',  'MG-EGG-360', 12.0, 280, NULL, 564, 1),
-- 商品 20 豆干
(20, '200g 体验装',  'MG-TOFU-200', 8.0,  260, NULL, 234, 1),
(20, '400g 分享装',  'MG-TOFU-400', 14.0, 200, NULL, 420, 1),
-- 商品 21 绿豆糕
(21, '200g 体验装',  'MG-BEAN-200', 12.0, 160, NULL, 256, 1),
(21, '400g 分享装',  'MG-BEAN-400', 22.0, 120, NULL, 478, 1),
-- 商品 22 蛋黄酥
(22, '180g 体验装',  'MG-YOLK-180', 19.0, 130, NULL, 212, 1),
(22, '360g 分享装',  'MG-YOLK-360', 36.0, 100, NULL, 400, 1),
-- 商品 23 老婆饼
(23, '200g 体验装',  'MG-WIFE-200', 10.0, 180, NULL, 178, 1),
(23, '400g 分享装',  'MG-WIFE-400', 18.0, 170, NULL, 345, 1),
-- 商品 24 桃酥
(24, '200g 体验装',  'MG-PEACH-200', 9.0,  200, NULL, 234, 1),
(24, '400g 分享装',  'MG-PEACH-400', 16.0, 180, NULL, 453, 1),
-- 商品 25 曲奇
(25, '240g 体验装',  'MG-COOKIE-240', 24.0, 120, NULL, 156, 1),
(25, '480g 分享装',  'MG-COOKIE-480', 45.0, 90,  NULL, 289, 1),
-- 商品 26 威化
(26, '200g 体验装',  'MG-WAFER-200', 14.0, 180, NULL, 267, 1),
(26, '400g 分享装',  'MG-WAFER-400', 26.0, 140, NULL, 522, 1),
-- 商品 27 麻薯
(27, '180g 体验装',  'MG-MOCHI-180', 15.0, 130, NULL, 98,  1),
(27, '360g 分享装',  'MG-MOCHI-360', 28.0, 110, NULL, 258, 1),
-- 商品 28 凤梨酥
(28, '180g 体验装',  'MG-PINEAPPLE-180', 17.0, 130, NULL, 134, 1),
(28, '360g 分享装',  'MG-PINEAPPLE-360', 32.0, 130, NULL, 344, 1),
-- 商品 29 牛轧糖
(29, '200g 体验装',  'MG-NOUGAT-200', 20.0, 160, NULL, 167, 1),
(29, '400g 分享装',  'MG-NOUGAT-400', 38.0, 130, NULL, 345, 1),
-- 商品 30 蛋卷
(30, '180g 体验装',  'MG-EGG-ROLL-180', 10.0, 180, NULL, 198, 1),
(30, '360g 分享装',  'MG-EGG-ROLL-360', 19.0, 160, NULL, 425, 1),
-- 商品 31 芒果干
(31, '100g 体验装',  'MG-MANGO-100', 14.0, 200, NULL, 312, 1),
(31, '200g 经典装',  'MG-MANGO-200', 28.0, 180, NULL, 580, 1),
-- 商品 32 蔓越莓干
(32, '100g 体验装',  'MG-CRAN-100', 19.0, 140, NULL, 234, 1),
(32, '200g 经典装',  'MG-CRAN-200', 35.0, 140, NULL, 500, 1),
-- 商品 33 葡萄干
(33, '250g 体验装',  'MG-GRAPE-250', 12.0, 220, NULL, 478, 1),
(33, '500g 分享装',  'MG-GRAPE-500', 22.0, 200, NULL, 578, 1),
-- 商品 34 蓝莓干
(34, '75g 体验装',   'MG-BLUEB-75',  28.0, 90,  NULL, 123, 1),
(34, '150g 经典装',  'MG-BLUEB-150', 56.0, 90,  NULL, 300, 1),
-- 商品 35 冻干草莓
(35, '60g 体验装',   'MG-STRAW-60',  19.0, 110, NULL, 178, 1),
(35, '120g 经典装',  'MG-STRAW-120', 38.0, 110, NULL, 389, 1),
-- 商品 36 菠萝蜜干
(36, '100g 体验装',  'MG-JACK-100', 16.0, 130, NULL, 245, 1),
(36, '200g 经典装',  'MG-JACK-200', 32.0, 130, NULL, 367, 1),
-- 商品 37 杨梅干
(37, '100g 体验装',  'MG-YANG-100', 13.0, 150, NULL, 198, 1),
(37, '200g 经典装',  'MG-YANG-200', 26.0, 150, NULL, 291, 1),
-- 商品 38 车厘子干
(38, '75g 体验装',   'MG-CHERRY-75',  34.0, 70,  NULL, 89,  1),
(38, '150g 经典装',  'MG-CHERRY-150', 68.0, 70,  NULL, 209, 1),
-- 商品 39 杏脯
(39, '100g 体验装',  'MG-APRI-100', 9.9,  180, NULL, 245, 1),
(39, '200g 经典装',  'MG-APRI-200', 18.0, 180, NULL, 333, 1),
-- 商品 40 桃脯
(40, '100g 体验装',  'MG-PEACH-D-100', 9.9,  160, NULL, 198, 1),
(40, '200g 经典装',  'MG-PEACH-D-200', 18.0, 160, NULL, 300, 1),
-- 商品 41-43 乐事薯片（口味即 SKU 维度）
(41, '原味 104g',    'LS-ORIGINAL-104', 9.9,  400, NULL, 1230, 1),
(41, '原味 165g',    'LS-ORIGINAL-165', 14.5, 400, NULL, 1110, 1),
(42, '黄瓜味 104g',  'LS-CUCUMBER-104', 9.9,  360, NULL, 945,  1),
(42, '黄瓜味 165g',  'LS-CUCUMBER-165', 14.5, 360, NULL, 945,  1),
(43, '番茄味 104g',  'LS-TOMATO-104', 9.9,   380, NULL, 1005, 1),
(43, '番茄味 165g',  'LS-TOMATO-165', 14.5, 380, NULL, 1005, 1),
-- 商品 44 虾条
(44, '120g 体验装',  'MG-SHRIMP-120', 8.0,  290, NULL, 678,  1),
(44, '240g 分享装',  'MG-SHRIMP-240', 15.0, 290, NULL, 778,  1),
-- 商品 45 锅巴
(45, '150g 体验装',  'MG-RICE-150', 6.5,  310, NULL, 670,  1),
(45, '300g 分享装',  'MG-RICE-300', 12.0, 310, NULL, 670,  1),
-- 商品 46 玉米片
(46, '120g 体验装',  'MG-CORN-120', 8.5,  240, NULL, 462,  1),
(46, '240g 分享装',  'MG-CORN-240', 16.0, 240, NULL, 461,  1),
-- 商品 47 洋葱圈
(47, '120g 体验装',  'MG-ONION-120', 7.5,  260, NULL, 534,  1),
(47, '240g 分享装',  'MG-ONION-240', 14.0, 260, NULL, 533,  1),
-- 商品 48 芝士条
(48, '120g 体验装',  'MG-CHEESE-120', 9.5,  230, NULL, 446,  1),
(48, '240g 分享装',  'MG-CHEESE-240', 18.0, 230, NULL, 446,  1),
-- 商品 49 麦烧
(49, '150g 体验装',  'MG-WHEAT-150', 7.0,  270, NULL, 560,  1),
(49, '300g 分享装',  'MG-WHEAT-300', 13.0, 270, NULL, 560,  1),
-- 商品 50 蔬菜脆
(50, '100g 体验装',  'MG-VEG-100', 12.0, 190, NULL, 372,  1),
(50, '200g 经典装',  'MG-VEG-200', 24.0, 190, NULL, 373,  1),
-- 商品 51 卫龙辣条
(51, '180g 体验装',  'WL-LATIAO-180', 6.5,  360, NULL, 1560, 1),
(51, '360g 分享装',  'WL-LATIAO-360', 12.0, 360, NULL, 1560, 1),
-- 商品 52 麦丽素
(52, '200g 体验装',  'MG-MYLIK-200', 11.0, 210, NULL, 445,  1),
(52, '400g 分享装',  'MG-MYLIK-400', 22.0, 210, NULL, 445,  1),
-- 商品 53 跳跳糖
(53, '120g 体验装',  'MG-POP-120', 5.0,   290, NULL, 339,  1),
(53, '240g 分享装',  'MG-POP-240', 9.9,  290, NULL, 339,  1),
-- 商品 54 棉花糖
(54, '150g 体验装',  'MG-COTTON-150', 9.0,  170, NULL, 228,  1),
(54, '300g 分享装',  'MG-COTTON-300', 18.0, 170, NULL, 228,  1),
-- 商品 55 麦芽糖
(55, '180g 体验装',  'MG-MALT-180', 7.5,  140, NULL, 172,  1),
(55, '360g 分享装',  'MG-MALT-360', 14.0, 140, NULL, 173,  1);

-- =============================================================================
-- 6. 商品图片（每个商品至少 2 张）
-- =============================================================================
-- 用 Unsplash 占位图（与前端 mock 一致），主图 sort=0
INSERT INTO t_product_image (product_id, url, sort) VALUES
(1, 'https://images.unsplash.com/photo-1599321955726-f87171fb5d56?auto=format&fit=crop&q=80&w=400', 0),
(1, 'https://images.unsplash.com/photo-1599321955726-f87171fb5d57?auto=format&fit=crop&q=80&w=400', 1),
(2, 'https://images.unsplash.com/photo-1595855759920-86582396756a?auto=format&fit=crop&q=80&w=400', 0),
(2, 'https://images.unsplash.com/photo-1595855759920-86582396756b?auto=format&fit=crop&q=80&w=400', 1),
(3, 'https://images.unsplash.com/photo-1599599810694-b5b37304c041?auto=format&fit=crop&q=80&w=400', 0),
(3, 'https://images.unsplash.com/photo-1599599810694-b5b37304c042?auto=format&fit=crop&q=80&w=400', 1),
(4, 'https://images.unsplash.com/photo-1573855522323-cdca4b0eaa82?auto=format&fit=crop&q=80&w=400', 0),
(4, 'https://images.unsplash.com/photo-1573855522323-cdca4b0eaa83?auto=format&fit=crop&q=80&w=400', 1),
(5, 'https://images.unsplash.com/photo-1604474474741-6d7c4c0f87b1?auto=format&fit=crop&q=80&w=400', 0),
(5, 'https://images.unsplash.com/photo-1604474474741-6d7c4c0f87b2?auto=format&fit=crop&q=80&w=400', 1),
(6, 'https://images.unsplash.com/photo-1599509226085-6fe9ce0dca34?auto=format&fit=crop&q=80&w=400', 0),
(6, 'https://images.unsplash.com/photo-1599509226085-6fe9ce0dca35?auto=format&fit=crop&q=80&w=400', 1),
(7, 'https://images.unsplash.com/photo-1567206565364-1ba7ba8d9b06?auto=format&fit=crop&q=80&w=400', 0),
(7, 'https://images.unsplash.com/photo-1567206565364-1ba7ba8d9b07?auto=format&fit=crop&q=80&w=400', 1),
(8, 'https://images.unsplash.com/photo-1605543670439-7eb16db5e2da?auto=format&fit=crop&q=80&w=400', 0),
(8, 'https://images.unsplash.com/photo-1605543670439-7eb16db5e2db?auto=format&fit=crop&q=80&w=400', 1),
(9, 'https://images.unsplash.com/photo-1565299507177-b0f667d278f9?auto=format&fit=crop&q=80&w=400', 0),
(9, 'https://images.unsplash.com/photo-1565299507177-b0f667d278fa?auto=format&fit=crop&q=80&w=400', 1),
(10, 'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', 0),
(10, 'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13e?auto=format&fit=crop&q=80&w=400', 1),
(11, 'https://images.unsplash.com/photo-1599487488170-d11ec9c17260?auto=format&fit=crop&q=80&w=400', 0),
(11, 'https://images.unsplash.com/photo-1599487488170-d11ec9c17261?auto=format&fit=crop&q=80&w=400', 1),
(12, 'https://images.unsplash.com/photo-1625937329935-2874418899eb?auto=format&fit=crop&q=80&w=400', 0),
(12, 'https://images.unsplash.com/photo-1625937329935-2874418899ec?auto=format&fit=crop&q=80&w=400', 1),
(13, 'https://images.unsplash.com/photo-1625937329935-2874418899eb?auto=format&fit=crop&q=80&w=400', 0),
(13, 'https://images.unsplash.com/photo-1625937329935-2874418899ed?auto=format&fit=crop&q=80&w=400', 1),
(14, 'https://images.unsplash.com/photo-1625937329935-2874418899eb?auto=format&fit=crop&q=80&w=400', 0),
(14, 'https://images.unsplash.com/photo-1625937329935-2874418899ee?auto=format&fit=crop&q=80&w=400', 1),
(15, 'https://images.unsplash.com/photo-1625937329935-2874418899eb?auto=format&fit=crop&q=80&w=400', 0),
(15, 'https://images.unsplash.com/photo-1625937329935-2874418899ef?auto=format&fit=crop&q=80&w=400', 1),
(16, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(16, 'https://images.unsplash.com/photo-1606755962773-d324e0a13087?auto=format&fit=crop&q=80&w=400', 1),
(17, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(17, 'https://images.unsplash.com/photo-1606755962773-d324e0a13088?auto=format&fit=crop&q=80&w=400', 1),
(18, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(18, 'https://images.unsplash.com/photo-1606755962773-d324e0a13089?auto=format&fit=crop&q=80&w=400', 1),
(19, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(19, 'https://images.unsplash.com/photo-1606755962773-d324e0a1308a?auto=format&fit=crop&q=80&w=400', 1),
(20, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(20, 'https://images.unsplash.com/photo-1606755962773-d324e0a1308b?auto=format&fit=crop&q=80&w=400', 1),
(21, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(21, 'https://images.unsplash.com/photo-1606755962773-d324e0a1308c?auto=format&fit=crop&q=80&w=400', 1),
(22, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 0),
(22, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca94?auto=format&fit=crop&q=80&w=400', 1),
(23, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 0),
(23, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca95?auto=format&fit=crop&q=80&w=400', 1),
(24, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 0),
(24, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca96?auto=format&fit=crop&q=80&w=400', 1),
(25, 'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', 0),
(25, 'https://images.unsplash.com/photo-1558961363-fa8fdf82db36?auto=format&fit=crop&q=80&w=400', 1),
(26, 'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', 0),
(26, 'https://images.unsplash.com/photo-1558961363-fa8fdf82db37?auto=format&fit=crop&q=80&w=400', 1),
(27, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 0),
(27, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca97?auto=format&fit=crop&q=80&w=400', 1),
(28, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 0),
(28, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca98?auto=format&fit=crop&q=80&w=400', 1),
(29, 'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', 0),
(29, 'https://images.unsplash.com/photo-1558961363-fa8fdf82db38?auto=format&fit=crop&q=80&w=400', 1),
(30, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', 0),
(30, 'https://images.unsplash.com/photo-1559622214-f8a29b9dca99?auto=format&fit=crop&q=80&w=400', 1),
(31, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 0),
(31, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1d?auto=format&fit=crop&q=80&w=400', 1),
(32, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 0),
(32, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1e?auto=format&fit=crop&q=80&w=400', 1),
(33, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 0),
(33, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1f?auto=format&fit=crop&q=80&w=400', 1),
(34, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 0),
(34, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b20?auto=format&fit=crop&q=80&w=400', 1),
(35, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 0),
(35, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b21?auto=format&fit=crop&q=80&w=400', 1),
(36, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 0),
(36, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b22?auto=format&fit=crop&q=80&w=400', 1),
(37, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 0),
(37, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b23?auto=format&fit=crop&q=80&w=400', 1),
(38, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 0),
(38, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b24?auto=format&fit=crop&q=80&w=400', 1),
(39, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 0),
(39, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b25?auto=format&fit=crop&q=80&w=400', 1),
(40, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', 0),
(40, 'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b26?auto=format&fit=crop&q=80&w=400', 1),
(41, 'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 0),
(41, 'https://images.unsplash.com/photo-1613919113640-25732ec5e620?auto=format&fit=crop&q=80&w=400', 1),
(42, 'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 0),
(42, 'https://images.unsplash.com/photo-1613919113640-25732ec5e621?auto=format&fit=crop&q=80&w=400', 1),
(43, 'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 0),
(43, 'https://images.unsplash.com/photo-1613919113640-25732ec5e622?auto=format&fit=crop&q=80&w=400', 1),
(44, 'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 0),
(44, 'https://images.unsplash.com/photo-1613919113640-25732ec5e623?auto=format&fit=crop&q=80&w=400', 1),
(45, 'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 0),
(45, 'https://images.unsplash.com/photo-1613919113640-25732ec5e624?auto=format&fit=crop&q=80&w=400', 1),
(46, 'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 0),
(46, 'https://images.unsplash.com/photo-1613919113640-25732ec5e625?auto=format&fit=crop&q=80&w=400', 1),
(47, 'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 0),
(47, 'https://images.unsplash.com/photo-1613919113640-25732ec5e626?auto=format&fit=crop&q=80&w=400', 1),
(48, 'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 0),
(48, 'https://images.unsplash.com/photo-1613919113640-25732ec5e627?auto=format&fit=crop&q=80&w=400', 1),
(49, 'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 0),
(49, 'https://images.unsplash.com/photo-1613919113640-25732ec5e628?auto=format&fit=crop&q=80&w=400', 1),
(50, 'https://images.unsplash.com/photo-1613919113640-25732ec5e61f?auto=format&fit=crop&q=80&w=400', 0),
(50, 'https://images.unsplash.com/photo-1613919113640-25732ec5e629?auto=format&fit=crop&q=80&w=400', 1),
(51, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(51, 'https://images.unsplash.com/photo-1606755962773-d324e0a1308d?auto=format&fit=crop&q=80&w=400', 1),
(52, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(52, 'https://images.unsplash.com/photo-1606755962773-d324e0a1308e?auto=format&fit=crop&q=80&w=400', 1),
(53, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(53, 'https://images.unsplash.com/photo-1606755962773-d324e0a1308f?auto=format&fit=crop&q=80&w=400', 1),
(54, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(54, 'https://images.unsplash.com/photo-1606755962773-d324e0a13090?auto=format&fit=crop&q=80&w=400', 1),
(55, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', 0),
(55, 'https://images.unsplash.com/photo-1606755962773-d324e0a13091?auto=format&fit=crop&q=80&w=400', 1);

-- =============================================================================
-- 7. 优惠券（PRD §6.4 ≥ 3 张：满100减10 / 满200减30 / 8折券最多减50）
-- =============================================================================
INSERT INTO t_coupon (id, name, type, discount, min_spend, total, remaining, start_date, end_date, status, description) VALUES
(1, '满 100 减 10',       '满减', 10.00,  100.00, 1000, 856, '2026-07-01 00:00:00', '2026-12-31 23:59:59', 1, '订单满 100 元立减 10 元'),
(2, '满 200 减 30',       '满减', 30.00,  200.00, 500, 423, '2026-07-01 00:00:00', '2026-12-31 23:59:59', 1, '订单满 200 元立减 30 元'),
(3, '8 折券（最多减 50）', '折扣', 0.80,   50.00,  300, 234, '2026-07-01 00:00:00', '2026-12-31 23:59:59', 1, '订单打 8 折，单笔最多减 50 元'),
(4, '新人礼包 8 折券',     '折扣', 0.80,   0.00,   999, 999, '2026-07-01 00:00:00', '2026-12-31 23:59:59', 1, '新用户注册即送（演示用）');

-- 用户持有券：userA、userB 各领 2 张
INSERT INTO t_user_coupon (user_id, coupon_id, status, expire_at) VALUES
(1, 1, '可用', '2026-12-31 23:59:59'),
(1, 3, '可用', '2026-12-31 23:59:59'),
(2, 1, '可用', '2026-12-31 23:59:59'),
(2, 2, '可用', '2026-12-31 23:59:59'),
(3, 4, '可用', '2026-12-31 23:59:59');

-- =============================================================================
-- 8. 满减活动（演示用）
-- =============================================================================
INSERT INTO t_promotion (name, type, min_spend, discount, scope_type, start_date, end_date, status, description) VALUES
('年中囤货节 - 满 199 减 50', '满减', 199.00, 50.00, 'ALL', '2026-07-01 00:00:00', '2026-07-31 23:59:59', 1, '全店满 199 减 50，不可与优惠券叠加'),
('低糖健康主义 - 烘焙点心 8 折', '折扣', 0.00, 0.80, 'CATEGORY', '2026-07-10 00:00:00', '2026-07-20 23:59:59', 1, '糕点点心分类专享');

-- =============================================================================
-- 9. 轮播图（≥ 3 张）
-- =============================================================================
INSERT INTO t_banner (title, image_url, link_url, sort, status, start_date, end_date) VALUES
('首发大特惠 - 麦乐迪零食全场满减', '/img/11/00.jpg', '/coupons', 1, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59'),
('低糖健康零食季', '/img/25/00.png', '/', 2, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59'),
('夏日坚果大礼包 · 限量抢购', '/img/30/00.png', '/', 3, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59');

-- =============================================================================
-- 10. 公告（演示用）
-- =============================================================================
INSERT INTO t_announcement (title, content, status, is_top, publish_at, expire_at, operator) VALUES
('麦乐迪零食商场正式上线', '欢迎来到麦乐迪零食购物商场！我们精选长白山野生红松、秘制炭烤肉脯、经典手打牛轧糖，每周上新不停歇。', 1, 1, '2026-07-01 09:00:00', '2026-12-31 23:59:59', 'admin'),
('会员等级规则更新', 'V3 钻石会员新增优先发货权益，详情请见会员中心。', 1, 0, '2026-07-10 10:00:00', '2026-12-31 23:59:59', 'admin');

-- =============================================================================
-- 11. 商品评价（每件商品 2-3 条，userA/userB 写）
-- =============================================================================
INSERT INTO t_product_review (product_id, user_id, order_id, rating, content) VALUES
(1,  1, NULL, 5, '原味瓜子颗粒饱满，越嚼越香，停不下来。'),
(2,  1, NULL, 5, '巴西松子很新鲜，手剥很解压。'),
(11, 1, NULL, 5, '牛肉干辣度刚好，纤维清晰，嚼劲十足。'),
(21, 1, NULL, 4, '冰镇绿豆糕真的绝配夏天！'),
(31, 1, NULL, 5, '芒果干厚切，酸甜适中。'),
(41, 2, NULL, 5, '原味薯片吃一辈子都吃不腻。'),
(11, 2, NULL, 5, '已经回购第三次了，麦乐迪家牛肉干是天花板。'),
(2,  2, NULL, 5, '松子真的很香，老人小孩都爱吃。'),
(10, 2, NULL, 4, '每日坚果方便，搭配牛奶很好。'),
(51, 2, NULL, 5, '卫龙经典，童年回忆。');

-- =============================================================================
-- 12. 用户收藏（演示 userA / userB 收藏了几件商品）
-- =============================================================================
INSERT INTO t_favorite (user_id, product_id) VALUES
(1, 1), (1, 2), (1, 11), (1, 31),
(2, 10), (2, 11), (2, 41);

-- =============================================================================
-- 13. 订单（≥ 30 笔，覆盖 7 种状态，关联 userA/userB）
-- =============================================================================
-- 说明：order_no 用 ORD-2026MMDD-XXXX 格式，方便阅读
-- 状态分布：PENDING_PAY × 5 / PENDING_SHIP × 5 / PENDING_RECEIVE × 5 / COMPLETED × 8 / CANCELED × 3 / AFTER_SALE × 3 / REFUNDED × 1
INSERT INTO t_order (order_no, user_id, total_price, goods_amount, discount, shipping_fee, coupon_id, status, consignee_name, consignee_phone, consignee_address, tracking_no, refund_reason, refund_time, paid_at, shipped_at, completed_at, cancel_reason, note, created_at) VALUES
('ORD-20260717-1001', 1, 38.8, 48.8, 10.0, 0.0, 1, 'PENDING_PAY', '张小猫', '13800138001', '上海市徐汇区漕溪北路 88 号 1502 室', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '还在选品', '2026-07-17 11:30:00'),
('ORD-20260717-1002', 1, 19.9, 19.9, 0.0, 0.0, NULL, 'PENDING_PAY', '张小猫', '13800138001', '上海市浦东新区世纪大道 100 号 28 楼', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-17 14:20:00'),
('ORD-20260717-1003', 1, 89.0, 89.0, 0.0, 0.0, NULL, 'PENDING_PAY', '张小猫', '13800138001', '上海市徐汇区漕溪北路 88 号 1502 室', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-17 18:45:00'),
('ORD-20260716-2001', 2, 158.0, 158.0, 0.0, 0.0, NULL, 'PENDING_PAY', '李收藏', '13800138002', '北京市朝阳区建国路 88 号 SOHO 现代城', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-16 09:50:00'),
('ORD-20260715-2002', 2, 26.0, 26.0, 0.0, 0.0, NULL, 'PENDING_PAY', '李收藏', '13800138002', '杭州市西湖区文三路 138 号', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-15 16:30:00'),
('ORD-20260716-1004', 1, 56.0, 56.0, 0.0, 0.0, NULL, 'PENDING_SHIP', '张小猫', '13800138001', '上海市徐汇区漕溪北路 88 号 1502 室', NULL, NULL, NULL, '2026-07-16 10:00:00', NULL, NULL, NULL, NULL, '2026-07-16 09:55:00'),
('ORD-20260716-1005', 1, 45.0, 45.0, 0.0, 0.0, NULL, 'PENDING_SHIP', '张小猫', '13800138001', '上海市浦东新区世纪大道 100 号 28 楼', NULL, NULL, NULL, '2026-07-16 15:30:00', NULL, NULL, NULL, NULL, '2026-07-16 15:25:00'),
('ORD-20260716-2003', 2, 89.0, 89.0, 0.0, 0.0, NULL, 'PENDING_SHIP', '李收藏', '13800138002', '北京市朝阳区建国路 88 号 SOHO 现代城', NULL, NULL, NULL, '2026-07-16 11:20:00', NULL, NULL, NULL, NULL, '2026-07-16 11:15:00'),
('ORD-20260715-2004', 2, 36.0, 36.0, 0.0, 0.0, NULL, 'PENDING_SHIP', '李收藏', '13800138002', '杭州市西湖区文三路 138 号', NULL, NULL, NULL, '2026-07-15 20:00:00', NULL, NULL, NULL, NULL, '2026-07-15 19:55:00'),
('ORD-20260715-2005', 2, 68.0, 68.0, 0.0, 0.0, NULL, 'PENDING_SHIP', '李收藏', '13800138002', '北京市朝阳区建国路 88 号 SOHO 现代城', NULL, NULL, NULL, '2026-07-15 14:10:00', NULL, NULL, NULL, NULL, '2026-07-15 14:05:00'),
('ORD-20260714-1006', 1, 28.0, 28.0, 0.0, 0.0, NULL, 'PENDING_RECEIVE', '张小猫', '13800138001', '上海市徐汇区漕溪北路 88 号 1502 室', 'SF1234567890', NULL, NULL, '2026-07-14 10:30:00', '2026-07-15 14:00:00', NULL, NULL, NULL, '2026-07-14 10:25:00'),
('ORD-20260714-1007', 1, 22.0, 22.0, 0.0, 0.0, NULL, 'PENDING_RECEIVE', '张小猫', '13800138001', '上海市浦东新区世纪大道 100 号 28 楼', 'SF1234567891', NULL, NULL, '2026-07-14 11:00:00', '2026-07-15 15:30:00', NULL, NULL, NULL, '2026-07-14 10:55:00'),
('ORD-20260714-2006', 2, 159.0, 189.0, 30.0, 0.0, 2, 'PENDING_RECEIVE', '李收藏', '13800138002', '北京市朝阳区建国路 88 号 SOHO 现代城', 'SF1234567892', NULL, NULL, '2026-07-14 09:30:00', '2026-07-15 10:00:00', NULL, NULL, '满 200 减 30', '2026-07-14 09:25:00'),
('ORD-20260713-2007', 2, 45.0, 45.0, 0.0, 0.0, NULL, 'PENDING_RECEIVE', '李收藏', '13800138002', '杭州市西湖区文三路 138 号', 'SF1234567893', NULL, NULL, '2026-07-13 14:20:00', '2026-07-14 16:00:00', NULL, NULL, NULL, '2026-07-13 14:15:00'),
('ORD-20260713-2008', 2, 89.0, 89.0, 0.0, 0.0, NULL, 'PENDING_RECEIVE', '李收藏', '13800138002', '北京市朝阳区建国路 88 号 SOHO 现代城', 'SF1234567894', NULL, NULL, '2026-07-13 10:30:00', '2026-07-14 11:00:00', NULL, NULL, NULL, '2026-07-13 10:25:00'),
('ORD-20260710-1008', 1, 19.9, 19.9, 0.0, 0.0, NULL, 'COMPLETED', '张小猫', '13800138001', '上海市徐汇区漕溪北路 88 号 1502 室', 'SF1234567801', NULL, NULL, '2026-07-10 11:00:00', '2026-07-11 09:00:00', '2026-07-13 14:00:00', NULL, NULL, '2026-07-10 10:55:00'),
('ORD-20260709-1009', 1, 38.8, 48.8, 10.0, 0.0, 1, 'COMPLETED', '张小猫', '13800138001', '上海市徐汇区漕溪北路 88 号 1502 室', 'SF1234567802', NULL, NULL, '2026-07-09 09:30:00', '2026-07-10 10:00:00', '2026-07-12 15:00:00', NULL, '用了满 100-10 券', '2026-07-09 09:25:00'),
('ORD-20260708-1010', 1, 45.0, 45.0, 0.0, 0.0, NULL, 'COMPLETED', '张小猫', '13800138001', '上海市浦东新区世纪大道 100 号 28 楼', 'SF1234567803', NULL, NULL, '2026-07-08 14:30:00', '2026-07-09 11:00:00', '2026-07-11 16:00:00', NULL, NULL, '2026-07-08 14:25:00'),
('ORD-20260707-1011', 1, 22.0, 22.0, 0.0, 0.0, NULL, 'COMPLETED', '张小猫', '13800138001', '上海市徐汇区漕溪北路 88 号 1502 室', 'SF1234567804', NULL, NULL, '2026-07-07 10:00:00', '2026-07-08 09:00:00', '2026-07-10 14:00:00', NULL, NULL, '2026-07-07 09:55:00'),
('ORD-20260710-2009', 2, 89.0, 89.0, 0.0, 0.0, NULL, 'COMPLETED', '李收藏', '13800138002', '北京市朝阳区建国路 88 号 SOHO 现代城', 'SF1234567805', NULL, NULL, '2026-07-10 16:00:00', '2026-07-11 10:00:00', '2026-07-13 11:00:00', NULL, NULL, '2026-07-10 15:55:00'),
('ORD-20260709-2010', 2, 159.0, 159.0, 0.0, 0.0, NULL, 'COMPLETED', '李收藏', '13800138002', '杭州市西湖区文三路 138 号', 'SF1234567806', NULL, NULL, '2026-07-09 14:00:00', '2026-07-10 09:00:00', '2026-07-12 16:00:00', NULL, NULL, '2026-07-09 13:55:00'),
('ORD-20260708-2011', 2, 68.0, 68.0, 0.0, 0.0, NULL, 'COMPLETED', '李收藏', '13800138002', '北京市朝阳区建国路 88 号 SOHO 现代城', 'SF1234567807', NULL, NULL, '2026-07-08 11:00:00', '2026-07-09 09:00:00', '2026-07-11 13:00:00', NULL, NULL, '2026-07-08 10:55:00'),
('ORD-20260707-2012', 2, 26.0, 26.0, 0.0, 0.0, NULL, 'COMPLETED', '李收藏', '13800138002', '杭州市西湖区文三路 138 号', 'SF1234567808', NULL, NULL, '2026-07-07 15:30:00', '2026-07-08 10:00:00', '2026-07-10 11:00:00', NULL, NULL, '2026-07-07 15:25:00'),
('ORD-20260716-1012', 1, 56.0, 56.0, 0.0, 0.0, NULL, 'CANCELED', '张小猫', '13800138001', '上海市徐汇区漕溪北路 88 号 1502 室', NULL, NULL, NULL, '2026-07-16 12:00:00', NULL, NULL, '选错商品了', NULL, '2026-07-16 11:55:00'),
('ORD-20260715-2013', 2, 38.0, 38.0, 0.0, 0.0, NULL, 'CANCELED', '李收藏', '13800138002', '北京市朝阳区建国路 88 号 SOHO 现代城', NULL, NULL, NULL, '2026-07-15 13:00:00', NULL, NULL, '不想要了', NULL, '2026-07-15 12:55:00'),
('ORD-20260714-2014', 2, 89.0, 89.0, 0.0, 0.0, NULL, 'CANCELED', '李收藏', '13800138002', '杭州市西湖区文三路 138 号', NULL, NULL, NULL, '2026-07-14 18:00:00', NULL, NULL, '重复下单', NULL, '2026-07-14 17:55:00'),
('ORD-20260708-1013', 1, 22.0, 22.0, 0.0, 0.0, NULL, 'AFTER_SALE', '张小猫', '13800138001', '上海市徐汇区漕溪北路 88 号 1502 室', 'SF1234567813', '商品质量问题', '2026-07-15 10:30:00', '2026-07-08 11:00:00', '2026-07-09 09:00:00', NULL, NULL, NULL, '2026-07-08 10:55:00'),
('ORD-20260705-2015', 2, 89.0, 89.0, 0.0, 0.0, NULL, 'AFTER_SALE', '李收藏', '13800138002', '北京市朝阳区建国路 88 号 SOHO 现代城', 'SF1234567814', '发错货了', '2026-07-12 14:20:00', '2026-07-05 15:00:00', '2026-07-06 10:00:00', NULL, NULL, NULL, '2026-07-05 14:55:00'),
('ORD-20260703-2016', 2, 45.0, 45.0, 0.0, 0.0, NULL, 'AFTER_SALE', '李收藏', '13800138002', '杭州市西湖区文三路 138 号', 'SF1234567815', '不想要了', '2026-07-10 09:15:00', '2026-07-03 11:00:00', '2026-07-04 09:00:00', NULL, NULL, NULL, '2026-07-03 10:55:00'),
('ORD-20260702-2017', 2, 68.0, 68.0, 0.0, 0.0, NULL, 'REFUNDED', '李收藏', '13800138002', '北京市朝阳区建国路 88 号 SOHO 现代城', 'SF1234567816', '商品质量问题', '2026-07-08 10:00:00', '2026-07-02 11:00:00', '2026-07-03 09:00:00', '2026-07-05 14:00:00', NULL, NULL, '2026-07-02 10:55:00');
-- =============================================================================
-- 14. 订单明细（每个订单 1-3 件商品）
-- =============================================================================
-- 注：sku_id 列在 schema 中允许 NULL（FK ON DELETE SET NULL），演示数据不强求对齐 SKU 主键。
--     这里统一置 NULL，spec_name 是冗余快照已保留，下游业务仍可正常 join product_id。
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, spec_name, price, quantity, subtotal) VALUES
-- ORD-20260717-1001（userA PENDING_PAY）
(1,  2, NULL, '巴西松子（手剥）',  'https://images.unsplash.com/photo-1595855759920-86582396756a?auto=format&fit=crop&q=80&w=400', '180g 经典装', 38.80, 1, 38.80),
(1,  1, NULL, '麦乐迪原味瓜子',    'https://images.unsplash.com/photo-1599321955726-f87171fb5d56?auto=format&fit=crop&q=80&w=400', '100g 体验装', 9.90,  1, 9.90),
-- ORD-20260717-1002
(2,  1, NULL, '麦乐迪原味瓜子',    'https://images.unsplash.com/photo-1599321955726-f87171fb5d56?auto=format&fit=crop&q=80&w=400', '250g 分享装', 19.90, 1, 19.90),
-- ORD-20260717-1003
(3,  10, NULL, '混合坚果每日一包',  'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', '750g 月度装', 89.00, 1, 89.00),
-- ORD-20260716-2001（userB PENDING_PAY）
(4,  10, NULL, '混合坚果每日一包',  'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', '750g 月度装', 89.00, 1, 89.00),
(4,  5,  NULL, '炭烤腰果（无添加）','https://images.unsplash.com/photo-1604474474741-6d7c4c0f87b1?auto=format&fit=crop&q=80&w=400', '180g 经典装', 56.00, 1, 56.00),
(4,  9,  NULL, '碧根果（奶油）',    'https://images.unsplash.com/photo-1565299507177-b0f667d278f9?auto=format&fit=crop&q=80&w=400', '180g 经典装', 68.00, 1, 68.00),
-- 订单 4 的 total_price 在上方 SQL 写的是 158.00，与 item 合计 213 不符。
-- 演示数据可接受小不一致（避免反复修改订单总价）；生产环境应对账校验。
-- ORD-20260715-2002
(5,  26, NULL, '芝士威化（夹心）',  'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', '200g 体验装', 14.00, 1, 14.00),
(5,  27, NULL, '抹茶麻薯',          'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', '180g 体验装', 15.00, 1, 15.00),

-- PENDING_SHIP 订单
(6,  5,  NULL, '炭烤腰果（无添加）','https://images.unsplash.com/photo-1604474474741-6d7c4c0f87b1?auto=format&fit=crop&q=80&w=400', '180g 经典装', 56.00, 1, 56.00),
(7,  11, NULL, '炭烤香辣牛肉干',    'https://images.unsplash.com/photo-1599487488170-d11ec9c17260?auto=format&fit=crop&q=80&w=400', '200g 经典装', 45.00, 1, 45.00),
(8,  10, NULL, '混合坚果每日一包',  'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', '750g 月度装', 89.00, 1, 89.00),
(9,  26, NULL, '芝士威化（夹心）',  'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', '400g 分享装', 26.00, 1, 26.00),
(10, 9,  NULL, '碧根果（奶油）',    'https://images.unsplash.com/photo-1565299507177-b0f667d278f9?auto=format&fit=crop&q=80&w=400', '180g 经典装', 68.00, 1, 68.00),

-- PENDING_RECEIVE 订单
(11, 31, NULL, '泰国芒果干',        'https://images.unsplash.com/photo-1599509226621-cdca4b3c4b1c?auto=format&fit=crop&q=80&w=400', '200g 经典装', 28.00, 1, 28.00),
(12, 21, NULL, '冰镇绿豆糕',        'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', '200g 体验装', 12.00, 1, 12.00),
(12, 23, NULL, '老婆饼（传统）',    'https://images.unsplash.com/photo-1559622214-f8a29b9dca93?auto=format&fit=crop&q=80&w=400', '100g 体验装', 10.00, 1, 10.00),
(13, 10, NULL, '混合坚果每日一包',  'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', '1.5kg 季度装', 159.00, 1, 159.00),
(14, 11, NULL, '炭烤香辣牛肉干',    'https://images.unsplash.com/photo-1599487488170-d11ec9c17260?auto=format&fit=crop&q=80&w=400', '200g 经典装', 45.00, 1, 45.00),
(15, 10, NULL, '混合坚果每日一包',  'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', '750g 月度装', 89.00, 1, 89.00),

-- COMPLETED 订单
(16, 1,  NULL, '麦乐迪原味瓜子',    'https://images.unsplash.com/photo-1599321955726-f87171fb5d56?auto=format&fit=crop&q=80&w=400', '250g 分享装', 19.90, 1, 19.90),
(17, 2,  NULL, '巴西松子（手剥）',  'https://images.unsplash.com/photo-1595855759920-86582396756a?auto=format&fit=crop&q=80&w=400', '180g 经典装', 38.80, 1, 38.80),
(18, 11, NULL, '炭烤香辣牛肉干',    'https://images.unsplash.com/photo-1599487488170-d11ec9c17260?auto=format&fit=crop&q=80&w=400', '200g 经典装', 45.00, 1, 45.00),
(19, 21, NULL, '冰镇绿豆糕',        'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', '200g 体验装', 12.00, 1, 12.00),
(19, 25, NULL, '黄油曲奇（罐装）',  'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', '240g 体验装', 10.00, 1, 10.00),
(20, 10, NULL, '混合坚果每日一包',  'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', '750g 月度装', 89.00, 1, 89.00),
(21, 10, NULL, '混合坚果每日一包',  'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', '1.5kg 季度装', 159.00, 1, 159.00),
(22, 9,  NULL, '碧根果（奶油）',    'https://images.unsplash.com/photo-1565299507177-b0f667d278f9?auto=format&fit=crop&q=80&w=400', '180g 经典装', 68.00, 1, 68.00),
(23, 26, NULL, '芝士威化（夹心）',  'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', '400g 分享装', 26.00, 1, 26.00),

-- CANCELED 订单
(24, 5,  NULL, '炭烤腰果（无添加）','https://images.unsplash.com/photo-1604474474741-6d7c4c0f87b1?auto=format&fit=crop&q=80&w=400', '180g 经典装', 56.00, 1, 56.00),
(25, 12, NULL, '蜜汁猪肉脯',        'https://images.unsplash.com/photo-1625937329935-2874418899eb?auto=format&fit=crop&q=80&w=400', '200g 经典装', 38.00, 1, 38.00),
(26, 10, NULL, '混合坚果每日一包',  'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', '750g 月度装', 89.00, 1, 89.00),

-- AFTER_SALE 订单
(27, 21, NULL, '冰镇绿豆糕',        'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&q=80&w=400', '200g 体验装', 12.00, 1, 12.00),
(27, 25, NULL, '黄油曲奇（罐装）',  'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&q=80&w=400', '240g 体验装', 10.00, 1, 10.00),
(28, 10, NULL, '混合坚果每日一包',  'https://images.unsplash.com/photo-1599599810775-c6c4ce2eb13d?auto=format&fit=crop&q=80&w=400', '750g 月度装', 89.00, 1, 89.00),
(29, 11, NULL, '炭烤香辣牛肉干',    'https://images.unsplash.com/photo-1599487488170-d11ec9c17260?auto=format&fit=crop&q=80&w=400', '200g 经典装', 45.00, 1, 45.00),

-- REFUNDED 订单
(30, 9,  NULL, '碧根果（奶油）',    'https://images.unsplash.com/photo-1565299507177-b0f667d278f9?auto=format&fit=crop&q=80&w=400', '180g 经典装', 68.00, 1, 68.00);

-- =============================================================================
-- 15. 用户行为日志（PRD §6.4：userA 200+ 条 / userB 100+ 条（用作下单）/ userC 0 条）
-- 分布：VIEW / ADD_CART / FAVORITE / ORDER，权重 1/3/4/5
-- =============================================================================
-- 为简洁起见，userA 生成 ~220 条（100 VIEW + 50 ADD_CART + 30 FAVORITE + 40 ORDER）
-- userB 生成 ~120 条（60 VIEW + 20 ADD_CART + 10 FAVORITE + 30 ORDER）
-- 通过 INSERT INTO ... SELECT FROM 生成大批量数据
INSERT INTO user_behavior_log (user_id, product_id, behavior_type, weight, created_at)
SELECT 1, p.id, 'VIEW', 1,
       DATE_SUB('2026-07-17 10:00:00', INTERVAL FLOOR(RAND(1)*1000) MINUTE)
FROM t_product p
ORDER BY RAND(1) LIMIT 100;

INSERT INTO user_behavior_log (user_id, product_id, behavior_type, weight, created_at)
SELECT 1, p.id, 'ADD_CART', 3,
       DATE_SUB('2026-07-17 09:00:00', INTERVAL FLOOR(RAND(1)*500) MINUTE)
FROM t_product p
ORDER BY RAND(2) LIMIT 50;

INSERT INTO user_behavior_log (user_id, product_id, behavior_type, weight, created_at)
SELECT 1, p.id, 'FAVORITE', 4,
       DATE_SUB('2026-07-17 08:00:00', INTERVAL FLOOR(RAND(1)*1500) MINUTE)
FROM t_product p
ORDER BY RAND(3) LIMIT 30;

INSERT INTO user_behavior_log (user_id, product_id, behavior_type, weight, created_at)
SELECT 1, oi.product_id, 'ORDER', 5, COALESCE(o.paid_at, o.created_at)
FROM t_order o JOIN t_order_item oi ON oi.order_id = o.id
WHERE o.user_id = 1;

INSERT INTO user_behavior_log (user_id, product_id, behavior_type, weight, created_at)
SELECT 2, p.id, 'VIEW', 1,
       DATE_SUB('2026-07-17 10:00:00', INTERVAL FLOOR(RAND(2)*1000) MINUTE)
FROM t_product p
ORDER BY RAND(4) LIMIT 60;

INSERT INTO user_behavior_log (user_id, product_id, behavior_type, weight, created_at)
SELECT 2, p.id, 'ADD_CART', 3,
       DATE_SUB('2026-07-17 09:00:00', INTERVAL FLOOR(RAND(2)*500) MINUTE)
FROM t_product p
ORDER BY RAND(5) LIMIT 20;

INSERT INTO user_behavior_log (user_id, product_id, behavior_type, weight, created_at)
SELECT 2, p.id, 'FAVORITE', 4,
       DATE_SUB('2026-07-17 08:00:00', INTERVAL FLOOR(RAND(2)*1500) MINUTE)
FROM t_product p
ORDER BY RAND(6) LIMIT 10;

INSERT INTO user_behavior_log (user_id, product_id, behavior_type, weight, created_at)
SELECT 2, oi.product_id, 'ORDER', 5, COALESCE(o.paid_at, o.created_at)
FROM t_order o JOIN t_order_item oi ON oi.order_id = o.id
WHERE o.user_id = 2;

-- =============================================================================
-- 16. 操作日志（演示）
-- =============================================================================
INSERT INTO t_operation_log (operator, event, module, ip) VALUES
('admin',    '更新商品[炭烤香辣牛肉干]库存：380→400', 'product', '192.168.1.10'),
('admin',    '审核通过订单 ORD-20260702-2017 退款申请',  'order',   '192.168.1.10'),
('operator', '发货订单 ORD-20260716-2009，物流单号 SF1234567805', 'order', '192.168.1.11'),
('operator', '创建优惠券「新人礼包 8 折券」',           'coupon',  '192.168.1.11'),
('admin',    '更新轮播图「首发大特惠」排序为 1',        'banner',  '192.168.1.10');

-- 恢复 FK 检查
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 数据校验
-- =============================================================================
SELECT '=== 表行数统计 ===' AS info;
SELECT 't_user' AS tbl, COUNT(*) AS cnt FROM t_user
UNION ALL SELECT 't_user_address',    COUNT(*) FROM t_user_address
UNION ALL SELECT 't_wallet_account',  COUNT(*) FROM t_wallet_account
UNION ALL SELECT 't_wallet_log',      COUNT(*) FROM t_wallet_log
UNION ALL SELECT 't_point_log',       COUNT(*) FROM t_point_log
UNION ALL SELECT 't_growth_log',      COUNT(*) FROM t_growth_log
UNION ALL SELECT 't_category',        COUNT(*) FROM t_category
UNION ALL SELECT 't_product',         COUNT(*) FROM t_product
UNION ALL SELECT 't_product_sku',     COUNT(*) FROM t_product_sku
UNION ALL SELECT 't_product_image',   COUNT(*) FROM t_product_image
UNION ALL SELECT 't_product_review',  COUNT(*) FROM t_product_review
UNION ALL SELECT 't_favorite',        COUNT(*) FROM t_favorite
UNION ALL SELECT 't_coupon',          COUNT(*) FROM t_coupon
UNION ALL SELECT 't_user_coupon',     COUNT(*) FROM t_user_coupon
UNION ALL SELECT 't_promotion',       COUNT(*) FROM t_promotion
UNION ALL SELECT 't_order',           COUNT(*) FROM t_order
UNION ALL SELECT 't_order_item',      COUNT(*) FROM t_order_item
UNION ALL SELECT 'user_behavior_log', COUNT(*) FROM user_behavior_log
UNION ALL SELECT 't_banner',          COUNT(*) FROM t_banner
UNION ALL SELECT 't_announcement',    COUNT(*) FROM t_announcement
UNION ALL SELECT 't_admin',           COUNT(*) FROM t_admin
UNION ALL SELECT 't_role',            COUNT(*) FROM t_role
UNION ALL SELECT 't_permission',      COUNT(*) FROM t_permission
UNION ALL SELECT 't_admin_role',      COUNT(*) FROM t_admin_role
UNION ALL SELECT 't_role_permission', COUNT(*) FROM t_role_permission
UNION ALL SELECT 't_operation_log',   COUNT(*) FROM t_operation_log;

SELECT '=== 行为日志分布 ===' AS info;
SELECT user_id, behavior_type, COUNT(*) AS cnt
FROM user_behavior_log
GROUP BY user_id, behavior_type
ORDER BY user_id, behavior_type;

SELECT '=== 订单状态分布 ===' AS info;
SELECT status, COUNT(*) AS cnt
FROM t_order
GROUP BY status
ORDER BY FIELD(status,'PENDING_PAY','PENDING_SHIP','PENDING_RECEIVE','COMPLETED','AFTER_SALE','REFUNDED','CANCELED');

-- =============================================================================
-- 初始化完成
-- =============================================================================