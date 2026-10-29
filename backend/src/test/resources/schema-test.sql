-- ============================================================
-- 测试用精简 Schema（H2 / MySQL 兼容模式）
-- 原始 schema 见 sql/schema.sql，本文件只保留被测 Mapper 涉及的表
-- 关键简化：JSON 列 → TEXT；ENGINE/CHARSET/COLLATE 等 MySQL 选项已剥离
-- ============================================================

DROP ALL OBJECTS;

-- 用户
CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(64),
    username VARCHAR(64),
    password VARCHAR(128),
    nickname VARCHAR(64),
    avatar VARCHAR(255),
    gender TINYINT,
    birthday DATE,
    rank_code VARCHAR(20) DEFAULT 'V0',
    points INT DEFAULT 0,
    growth_value INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    last_login_at DATETIME,
    join_date DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_phone ON t_user(phone);

-- 商品分类
CREATE TABLE t_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64),
    parent_id BIGINT,
    icon VARCHAR(255),
    sort INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 商品（tags JSON 简化 TEXT）
CREATE TABLE t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    category_id BIGINT,
    brand VARCHAR(128),
    price DECIMAL(10,2),
    market_price DECIMAL(10,2),
    description TEXT,
    cover_image VARCHAR(512),
    inventory INT DEFAULT 0,
    sales INT DEFAULT 0,
    tags TEXT,
    rating DECIMAL(3,2) DEFAULT 5.00,
    status TINYINT DEFAULT 1,
    origin VARCHAR(64),
    shelf_life VARCHAR(64),
    weight VARCHAR(32),
    ingredients VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 商品 SKU
CREATE TABLE t_product_sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT,
    spec_name VARCHAR(64),
    sku_code VARCHAR(32),
    price DECIMAL(10,2),
    stock INT DEFAULT 0,
    image VARCHAR(512),
    sales INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 购物车项
CREATE TABLE t_cart_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    product_id BIGINT,
    sku_id BIGINT,
    quantity INT DEFAULT 1,
    price DECIMAL(10,2),
    spec_name VARCHAR(64),
    selected TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 优惠券
CREATE TABLE t_coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128),
    type VARCHAR(16),
    discount DECIMAL(10,2),
    min_spend DECIMAL(10,2),
    total INT DEFAULT 0,
    remaining INT DEFAULT 0,
    start_date DATETIME,
    end_date DATETIME,
    status TINYINT DEFAULT 1,
    description VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 用户领取记录（含 unique 约束，模拟迁移后状态）
CREATE TABLE t_user_coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    coupon_id BIGINT,
    status VARCHAR(16) DEFAULT '可用',
    used_at DATETIME,
    used_order_id BIGINT,
    expire_at DATETIME,
    claimed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_user_coupon ON t_user_coupon(user_id, coupon_id);

-- 订单
CREATE TABLE t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32),
    user_id BIGINT,
    total_price DECIMAL(10,2),
    goods_amount DECIMAL(10,2),
    discount DECIMAL(10,2),
    shipping_fee DECIMAL(10,2),
    coupon_id BIGINT,
    status VARCHAR(20),
    pay_method VARCHAR(20),
    trade_no VARCHAR(64),
    consignee_name VARCHAR(64),
    consignee_phone VARCHAR(20),
    consignee_address VARCHAR(255),
    tracking_no VARCHAR(64),
    refund_reason VARCHAR(255),
    refund_time DATETIME,
    paid_at DATETIME,
    shipped_at DATETIME,
    completed_at DATETIME,
    cancel_reason VARCHAR(255),
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_order_no ON t_order(order_no);

-- 订单项
CREATE TABLE t_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT,
    product_id BIGINT,
    sku_id BIGINT,
    product_name VARCHAR(255),
    product_image VARCHAR(512),
    spec_name VARCHAR(64),
    price DECIMAL(10,2),
    quantity INT,
    subtotal DECIMAL(10,2),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 钱包账户
CREATE TABLE t_wallet_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    balance DECIMAL(10,2) DEFAULT 0,
    frozen DECIMAL(10,2) DEFAULT 0,
    password VARCHAR(128),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_wallet_user ON t_wallet_account(user_id);

-- 钱包流水
CREATE TABLE t_wallet_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    change_amount DECIMAL(10,2),
    balance_after DECIMAL(10,2),
    type VARCHAR(20),
    reason VARCHAR(255),
    related_order_id BIGINT,
    operator VARCHAR(64) DEFAULT 'system',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 钱包充值订单
CREATE TABLE t_wallet_recharge_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    out_trade_no VARCHAR(64),
    user_id BIGINT,
    amount DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'PENDING',
    trade_no VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_out_trade_no ON t_wallet_recharge_order(out_trade_no);

-- 用户行为日志（extra JSON 简化 TEXT）
CREATE TABLE user_behavior_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    product_id BIGINT,
    behavior_type VARCHAR(20),
    weight INT DEFAULT 1,
    extra TEXT,
    client_ip VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_behavior ON user_behavior_log(user_id);
CREATE INDEX idx_product_behavior ON user_behavior_log(product_id);

-- 评价
CREATE TABLE t_product_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT,
    user_id BIGINT,
    order_id BIGINT,
    rating TINYINT,
    content TEXT,
    is_anonymous TINYINT DEFAULT 0,
    is_hidden TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 管理员
CREATE TABLE t_admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64),
    password VARCHAR(128),
    email VARCHAR(64),
    phone VARCHAR(20),
    display_name VARCHAR(64),
    status TINYINT DEFAULT 1,
    last_login_at DATETIME,
    last_login_ip VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_admin_username ON t_admin(username);
