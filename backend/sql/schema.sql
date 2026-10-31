-- =============================================================================
-- 麦乐迪零食购物商场系统 - 数据库 DDL
-- 数据库：melody_shop
-- 字符集：utf8mb4 / 引擎：InnoDB
-- 严格依照 PRD §6.1 实体清单 + §6.1.1 t_product_sku 字段补充 + §7.1 订单状态枚举
-- =============================================================================

SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS melody_shop DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE melody_shop;

-- 关闭 FK 检查，方便重建
SET FOREIGN_KEY_CHECKS = 0;

-- 售后多 Agent 工单（新增模块，放在最前 drop 以兼容重跑）
DROP TABLE IF EXISTS t_after_sale_event;
DROP TABLE IF EXISTS t_after_sale_ticket;

-- =============================================================================
-- 1. 用户与地址
-- =============================================================================

DROP TABLE IF EXISTS t_user_address;
DROP TABLE IF EXISTS t_wallet_log;
DROP TABLE IF EXISTS t_wallet_account;
DROP TABLE IF EXISTS t_growth_log;
DROP TABLE IF EXISTS t_point_log;
DROP TABLE IF EXISTS t_user_coupon;
DROP TABLE IF EXISTS t_coupon;
DROP TABLE IF EXISTS t_order_item;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_favorite;
DROP TABLE IF EXISTS t_cart_item;
DROP TABLE IF EXISTS t_product_review;
DROP TABLE IF EXISTS user_behavior_log;
DROP TABLE IF EXISTS t_product_sku;
DROP TABLE IF EXISTS t_product_image;
DROP TABLE IF EXISTS t_product;
DROP TABLE IF EXISTS t_category;
DROP TABLE IF EXISTS t_user;
DROP TABLE IF EXISTS t_promotion;
DROP TABLE IF EXISTS t_admin;
DROP TABLE IF EXISTS t_admin_role;
DROP TABLE IF EXISTS t_role_permission;
DROP TABLE IF EXISTS t_role;
DROP TABLE IF EXISTS t_permission;
DROP TABLE IF EXISTS t_banner;
DROP TABLE IF EXISTS t_announcement;
DROP TABLE IF EXISTS t_operation_log;

CREATE TABLE t_user (
  id              BIGINT       NOT NULL AUTO_INCREMENT      COMMENT '主键',
  phone           VARCHAR(20)  NOT NULL                      COMMENT '手机号（登录账号，唯一）',
  email           VARCHAR(100) DEFAULT NULL                 COMMENT '邮箱',
  username        VARCHAR(50)  DEFAULT NULL                 COMMENT '用户名',
  password        VARCHAR(100) NOT NULL                      COMMENT 'BCrypt 加密密码',
  nickname        VARCHAR(50)  DEFAULT NULL                 COMMENT '昵称',
  avatar          VARCHAR(255) DEFAULT NULL                 COMMENT '头像 URL',
  gender          TINYINT      DEFAULT 0                    COMMENT '性别 0未知 1男 2女',
  birthday        DATE         DEFAULT NULL                 COMMENT '生日',
  rank_code       VARCHAR(20)  DEFAULT 'V0'                 COMMENT '会员等级 V0/V1/V2/V3 (PRD §7.5)',
  points          INT          DEFAULT 0                    COMMENT '积分余额 (PRD §7.3)',
  growth_value    INT          DEFAULT 0                    COMMENT '成长值 (PRD §7.5)',
  status          TINYINT      DEFAULT 1                    COMMENT '状态 1正常 0黑名单',
  last_login_at   DATETIME     DEFAULT NULL                 COMMENT '最近登录时间',
  join_date       DATETIME     DEFAULT NULL                 COMMENT '注册时间',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前台用户表';

CREATE TABLE t_user_address (
  id          BIGINT       NOT NULL AUTO_INCREMENT          COMMENT '主键',
  user_id     BIGINT       NOT NULL                          COMMENT '所属用户 ID',
  name        VARCHAR(50)  NOT NULL                          COMMENT '收货人',
  phone       VARCHAR(20)  NOT NULL                          COMMENT '收货人手机',
  province    VARCHAR(50)  NOT NULL                          COMMENT '省',
  city        VARCHAR(50)  NOT NULL                          COMMENT '市',
  district    VARCHAR(50)  NOT NULL                          COMMENT '区/县',
  detail      VARCHAR(255) NOT NULL                          COMMENT '详细地址',
  `tag`       VARCHAR(20)  DEFAULT '家'                     COMMENT '地址标签 (家/公司/学校)',
  is_default  TINYINT      DEFAULT 0                        COMMENT '是否默认地址 1是 0否',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  CONSTRAINT fk_addr_user FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址表';

-- =============================================================================
-- 2. 商品域
-- =============================================================================

CREATE TABLE t_category (
  id          BIGINT       NOT NULL AUTO_INCREMENT          COMMENT '主键',
  name        VARCHAR(50)  NOT NULL                          COMMENT '分类名称',
  parent_id   BIGINT       DEFAULT NULL                     COMMENT '父分类 ID，顶级为 NULL',
  icon        VARCHAR(255) DEFAULT NULL                     COMMENT '分类图标 URL',
  sort        INT          DEFAULT 0                        COMMENT '排序权重，数字越大越靠前',
  status      TINYINT      DEFAULT 1                        COMMENT '状态 1启用 0禁用',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_parent (parent_id),
  CONSTRAINT fk_cat_parent FOREIGN KEY (parent_id) REFERENCES t_category (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表（多级树）';

CREATE TABLE t_product (
  id           BIGINT         NOT NULL AUTO_INCREMENT       COMMENT '主键',
  name         VARCHAR(100)   NOT NULL                       COMMENT '商品名',
  category_id  BIGINT         NOT NULL                       COMMENT '所属分类 ID',
  brand        VARCHAR(50)    DEFAULT NULL                  COMMENT '品牌',
  price        DECIMAL(10, 2) NOT NULL                       COMMENT '默认售价',
  market_price DECIMAL(10, 2) DEFAULT NULL                  COMMENT '市场价/划线价',
  description  TEXT           DEFAULT NULL                  COMMENT '商品描述',
  cover_image  VARCHAR(255)   DEFAULT NULL                  COMMENT '封面图（首图）',
  inventory    INT            DEFAULT 0                     COMMENT '默认库存（SKU 库存可独立）',
  sales        INT            DEFAULT 0                     COMMENT '累计销量（冗余字段，便于排序）',
  tags         JSON           DEFAULT NULL                  COMMENT '标签 JSON 数组，如 ["低糖健康","会员特惠"]',
  rating       DECIMAL(3, 2)  DEFAULT 5.00                 COMMENT '平均评分 0-5',
  status       TINYINT        DEFAULT 1                     COMMENT '上下架状态 1上架 0下架',
  origin       VARCHAR(100)   DEFAULT NULL                  COMMENT '产地',
  shelf_life   VARCHAR(50)    DEFAULT NULL                  COMMENT '保质期',
  weight       VARCHAR(50)    DEFAULT NULL                  COMMENT '净含量',
  ingredients  TEXT           DEFAULT NULL                  COMMENT '配料表',
  created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_category (category_id),
  KEY idx_sales (sales),
  CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES t_category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品主信息表';

CREATE TABLE t_product_sku (
  id          BIGINT         NOT NULL AUTO_INCREMENT       COMMENT '主键（PRD §6.1.1 字段 sku_id 改名 id 统一规范）',
  product_id  BIGINT         NOT NULL                       COMMENT '所属商品 ID',
  spec_name   VARCHAR(64)   NOT NULL                       COMMENT '规格名（如"麻辣味/60g"）',
  sku_code    VARCHAR(32)   DEFAULT NULL                  COMMENT 'SKU 编码',
  price       DECIMAL(10, 2) NOT NULL                       COMMENT '该 SKU 的售价',
  stock       INT            NOT NULL DEFAULT 0             COMMENT '该 SKU 的库存',
  image       VARCHAR(255)   DEFAULT NULL                  COMMENT '该 SKU 对应图片（默认沿用商品主图）',
  sales       INT            DEFAULT 0                     COMMENT '该 SKU 销量（冗余字段，便于排序）',
  status      TINYINT        NOT NULL DEFAULT 1             COMMENT '上下架状态 0下架 1上架',
  created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_product (product_id),
  CONSTRAINT fk_sku_prod FOREIGN KEY (product_id) REFERENCES t_product (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格/SKU 表（PRD §6.1.1 V1.1 补充）';

CREATE TABLE t_product_image (
  id          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  product_id  BIGINT       NOT NULL                         COMMENT '所属商品 ID',
  url         VARCHAR(255) NOT NULL                         COMMENT '图片 URL',
  sort        INT          DEFAULT 0                       COMMENT '排序，数字越小越靠前',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_product (product_id),
  CONSTRAINT fk_img_prod FOREIGN KEY (product_id) REFERENCES t_product (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片表（一对多）';

CREATE TABLE t_product_review (
  id          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  product_id  BIGINT       NOT NULL                         COMMENT '商品 ID',
  user_id     BIGINT       NOT NULL                         COMMENT '评价用户 ID',
  order_id    BIGINT       DEFAULT NULL                    COMMENT '关联订单 ID（仅已购买用户可评）',
  rating      TINYINT      NOT NULL                         COMMENT '评分 1-5',
  content     TEXT         NOT NULL                         COMMENT '评价内容',
  is_anonymous TINYINT     DEFAULT 0                       COMMENT '是否匿名 1是 0否',
  is_hidden   TINYINT      DEFAULT 0                       COMMENT '是否隐藏 1是 0否（后台审核）',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_product (product_id),
  KEY idx_user (user_id),
  CONSTRAINT fk_rev_prod FOREIGN KEY (product_id) REFERENCES t_product (id),
  CONSTRAINT fk_rev_user FOREIGN KEY (user_id)    REFERENCES t_user (id),
  CONSTRAINT fk_rev_order FOREIGN KEY (order_id) REFERENCES t_order (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';

-- =============================================================================
-- 3. 购物车、收藏、订单
-- =============================================================================

CREATE TABLE t_cart_item (
  id          BIGINT         NOT NULL AUTO_INCREMENT       COMMENT '主键',
  user_id     BIGINT         NOT NULL                       COMMENT '所属用户 ID',
  product_id  BIGINT         NOT NULL                       COMMENT '商品 ID',
  sku_id      BIGINT         NOT NULL                       COMMENT 'SKU ID',
  quantity    INT            NOT NULL DEFAULT 1             COMMENT '数量',
  price       DECIMAL(10, 2) NOT NULL                       COMMENT '加购时价格（快照）',
  spec_name   VARCHAR(64)    NOT NULL                       COMMENT '规格名（冗余）',
  selected    TINYINT        DEFAULT 1                     COMMENT '是否选中 (F-U-034) 1是 0否',
  created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_sku (user_id, sku_id),
  KEY idx_user (user_id),
  CONSTRAINT fk_cart_user FOREIGN KEY (user_id)    REFERENCES t_user (id)    ON DELETE CASCADE,
  CONSTRAINT fk_cart_prod FOREIGN KEY (product_id) REFERENCES t_product (id),
  CONSTRAINT fk_cart_sku  FOREIGN KEY (sku_id)     REFERENCES t_product_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车明细表';

CREATE TABLE t_favorite (
  id          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  user_id     BIGINT       NOT NULL                         COMMENT '用户 ID',
  product_id  BIGINT       NOT NULL                         COMMENT '商品 ID',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_prod (user_id, product_id),
  KEY idx_user (user_id),
  CONSTRAINT fk_fav_user FOREIGN KEY (user_id)    REFERENCES t_user (id)    ON DELETE CASCADE,
  CONSTRAINT fk_fav_prod FOREIGN KEY (product_id) REFERENCES t_product (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏表';

CREATE TABLE t_order (
  id                BIGINT         NOT NULL AUTO_INCREMENT   COMMENT '主键',
  order_no          VARCHAR(32)    NOT NULL                  COMMENT '订单号（业务编号，唯一）',
  user_id           BIGINT         NOT NULL                  COMMENT '下单用户 ID',
  total_price       DECIMAL(10, 2) NOT NULL                  COMMENT '订单实付总金额',
  goods_amount      DECIMAL(10, 2) NOT NULL                  COMMENT '商品总金额（优惠前）',
  discount          DECIMAL(10, 2) DEFAULT 0.00              COMMENT '优惠金额',
  shipping_fee      DECIMAL(10, 2) DEFAULT 0.00              COMMENT '运费',
  coupon_id         BIGINT         DEFAULT NULL              COMMENT '使用的优惠券 ID',
  status            VARCHAR(20)    NOT NULL                  COMMENT '订单状态：PENDING_PAY/PENDING_SHIP/PENDING_RECEIVE/COMPLETED/AFTER_SALE/REFUNDED/CANCELED (PRD §7.1)',
  consignee_name    VARCHAR(50)    DEFAULT NULL              COMMENT '收货人',
  consignee_phone   VARCHAR(20)    DEFAULT NULL              COMMENT '收货人手机',
  consignee_address VARCHAR(255)   DEFAULT NULL              COMMENT '完整收货地址',
  tracking_no       VARCHAR(50)    DEFAULT NULL              COMMENT '物流单号',
  refund_reason     VARCHAR(255)   DEFAULT NULL              COMMENT '退款原因 (F-U-044)',
  refund_time       DATETIME       DEFAULT NULL              COMMENT '申请退款时间',
  paid_at           DATETIME       DEFAULT NULL              COMMENT '支付完成时间',
  shipped_at        DATETIME       DEFAULT NULL              COMMENT '发货时间',
  completed_at      DATETIME       DEFAULT NULL              COMMENT '确认收货/订单完成时间',
  cancel_reason     VARCHAR(255)   DEFAULT NULL              COMMENT '取消原因',
  note              VARCHAR(255)   DEFAULT NULL              COMMENT '订单备注',
  created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user (user_id),
  KEY idx_status (status),
  CONSTRAINT fk_ord_user  FOREIGN KEY (user_id)   REFERENCES t_user (id),
  CONSTRAINT fk_ord_coupon FOREIGN KEY (coupon_id) REFERENCES t_coupon (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表 (PRD §7.1)';

CREATE TABLE t_order_item (
  id            BIGINT         NOT NULL AUTO_INCREMENT     COMMENT '主键',
  order_id      BIGINT         NOT NULL                    COMMENT '所属订单 ID',
  product_id    BIGINT         NOT NULL                    COMMENT '商品 ID',
  sku_id        BIGINT         DEFAULT NULL               COMMENT 'SKU ID（可空，兼容老数据）',
  product_name  VARCHAR(100)   NOT NULL                    COMMENT '商品名（冗余快照）',
  product_image VARCHAR(255)   DEFAULT NULL                COMMENT '商品图（冗余快照）',
  spec_name     VARCHAR(64)    DEFAULT NULL                COMMENT '规格名（冗余快照）',
  price         DECIMAL(10, 2) NOT NULL                    COMMENT '下单单价（冗余快照）',
  quantity      INT            NOT NULL                    COMMENT '数量',
  subtotal      DECIMAL(10, 2) NOT NULL                    COMMENT '小计 = price × quantity',
  created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_product (product_id),
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id)   REFERENCES t_order (id)       ON DELETE CASCADE,
  CONSTRAINT fk_oi_prod  FOREIGN KEY (product_id) REFERENCES t_product (id),
  CONSTRAINT fk_oi_sku   FOREIGN KEY (sku_id)     REFERENCES t_product_sku (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品明细表';

-- =============================================================================
-- 4. 营销：优惠券、满减活动
-- =============================================================================

CREATE TABLE t_coupon (
  id          BIGINT         NOT NULL AUTO_INCREMENT       COMMENT '主键',
  name        VARCHAR(100)   NOT NULL                       COMMENT '优惠券名称',
  type        VARCHAR(20)    NOT NULL                       COMMENT '类型：满减/折扣 (PRD §7.4)',
  discount    DECIMAL(10, 2) NOT NULL                       COMMENT '面额：满减券为减金额，折扣券为折扣率(如 0.8)',
  min_spend   DECIMAL(10, 2) DEFAULT 0                     COMMENT '使用门槛，满 X 元可用',
  total       INT            NOT NULL DEFAULT 0             COMMENT '发行总量',
  remaining   INT            NOT NULL DEFAULT 0             COMMENT '剩余库存',
  start_date  DATETIME       DEFAULT NULL                   COMMENT '活动开始时间',
  end_date    DATETIME       DEFAULT NULL                   COMMENT '活动结束时间',
  status      TINYINT        DEFAULT 1                     COMMENT '状态 1启用 0禁用',
  description VARCHAR(255)   DEFAULT NULL                   COMMENT '说明',
  created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券定义表 (PRD §7.4)';

CREATE TABLE t_user_coupon (
  id            BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  user_id       BIGINT       NOT NULL                         COMMENT '用户 ID',
  coupon_id     BIGINT       NOT NULL                         COMMENT '优惠券 ID',
  status        VARCHAR(20)  NOT NULL DEFAULT '可用'           COMMENT '状态：可用/已用/过期',
  used_at       DATETIME     DEFAULT NULL                     COMMENT '使用时间',
  used_order_id BIGINT       DEFAULT NULL                     COMMENT '使用该券的订单 ID',
  expire_at     DATETIME     DEFAULT NULL                     COMMENT '过期时间',
  claimed_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_coupon (user_id, coupon_id),
  KEY idx_user (user_id),
  KEY idx_coupon (coupon_id),
  CONSTRAINT fk_uc_user   FOREIGN KEY (user_id)   REFERENCES t_user (id)   ON DELETE CASCADE,
  CONSTRAINT fk_uc_coupon FOREIGN KEY (coupon_id) REFERENCES t_coupon (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户持有优惠券表';

CREATE TABLE t_promotion (
  id           BIGINT         NOT NULL AUTO_INCREMENT      COMMENT '主键',
  name         VARCHAR(100)   NOT NULL                      COMMENT '活动名称',
  type         VARCHAR(20)    NOT NULL                      COMMENT '类型：满减/折扣',
  min_spend    DECIMAL(10, 2) DEFAULT 0                    COMMENT '门槛：满 X 元',
  discount     DECIMAL(10, 2) DEFAULT 0                    COMMENT '优惠：减 Y 元 或 折扣率',
  scope_type   VARCHAR(20)    DEFAULT 'ALL'                 COMMENT '范围：ALL 全场 / CATEGORY 分类 / PRODUCT 指定商品',
  scope_value  JSON           DEFAULT NULL                  COMMENT '范围 ID 列表（按 scope_type 解释）',
  start_date   DATETIME       DEFAULT NULL                  COMMENT '开始时间',
  end_date     DATETIME       DEFAULT NULL                  COMMENT '结束时间',
  status       TINYINT        DEFAULT 1                     COMMENT '状态 1启用 0禁用',
  banner_url   VARCHAR(255)   DEFAULT NULL                  COMMENT '活动 banner 图',
  description  VARCHAR(255)   DEFAULT NULL                  COMMENT '活动说明',
  created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满减活动表';

-- =============================================================================
-- 5. 钱包 / 积分 / 成长值
-- =============================================================================

CREATE TABLE t_wallet_account (
  id          BIGINT         NOT NULL AUTO_INCREMENT       COMMENT '主键',
  user_id     BIGINT         NOT NULL                       COMMENT '所属用户 ID（一对一）',
  balance     DECIMAL(10, 2) NOT NULL DEFAULT 0            COMMENT '可用余额（元）',
  frozen      DECIMAL(10, 2) NOT NULL DEFAULT 0            COMMENT '冻结金额（提现/退款中）',
  password    VARCHAR(100)   DEFAULT NULL                  COMMENT '支付密码（BCrypt，可空）',
  created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id),
  CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户虚拟钱包账户 (PRD §5.5 F-C-003)';

CREATE TABLE t_wallet_log (
  id              BIGINT         NOT NULL AUTO_INCREMENT   COMMENT '主键',
  user_id         BIGINT         NOT NULL                   COMMENT '用户 ID',
  change_amount   DECIMAL(10, 2) NOT NULL                   COMMENT '变动金额（正为入账，负为出账）',
  balance_after   DECIMAL(10, 2) NOT NULL                   COMMENT '变动后余额',
  type            VARCHAR(30)    NOT NULL                   COMMENT '类型：RECHARGE 充值 / PAY 支付 / REFUND 退款 / ADMIN_ADJUST 管理员调整',
  reason          VARCHAR(255)   DEFAULT NULL               COMMENT '备注',
  related_order_id BIGINT        DEFAULT NULL               COMMENT '关联订单 ID（支付/退款时）',
  operator        VARCHAR(50)    DEFAULT 'system'           COMMENT '操作人',
  created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  CONSTRAINT fk_wl_user  FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_wl_order FOREIGN KEY (related_order_id) REFERENCES t_order (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包流水表';

CREATE TABLE t_point_log (
  id              BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '主键',
  user_id         BIGINT       NOT NULL                        COMMENT '用户 ID',
  change_amount   INT          NOT NULL                        COMMENT '变动积分（正为入账，负为出账）',
  balance_after   INT          NOT NULL                        COMMENT '变动后积分余额',
  type            VARCHAR(30)  NOT NULL                        COMMENT '类型：ORDER 下单赠送 / REVIEW 评价 / REGISTER 注册礼包 / CANCEL 取消回退',
  reason          VARCHAR(255) DEFAULT NULL                    COMMENT '备注',
  related_order_id BIGINT      DEFAULT NULL                    COMMENT '关联订单 ID',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  CONSTRAINT fk_pl_user  FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_pl_order FOREIGN KEY (related_order_id) REFERENCES t_order (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表 (PRD §7.3)';

CREATE TABLE t_growth_log (
  id              BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '主键',
  user_id         BIGINT       NOT NULL                        COMMENT '用户 ID',
  change_amount   INT          NOT NULL                        COMMENT '变动成长值',
  balance_after   INT          NOT NULL                        COMMENT '变动后成长值',
  type            VARCHAR(30)  NOT NULL                        COMMENT '类型：ORDER 下单获得 / REFUND 退款扣减',
  reason          VARCHAR(255) DEFAULT NULL                    COMMENT '备注',
  related_order_id BIGINT      DEFAULT NULL                    COMMENT '关联订单 ID',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  CONSTRAINT fk_gl_user  FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_gl_order FOREIGN KEY (related_order_id) REFERENCES t_order (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长值流水表 (PRD §7.5)';

-- =============================================================================
-- 6. 推荐系统：行为日志
-- =============================================================================

CREATE TABLE user_behavior_log (
  id              BIGINT       NOT NULL AUTO_INCREMENT      COMMENT '主键',
  user_id         BIGINT       DEFAULT NULL                 COMMENT '用户 ID（游客为 NULL）',
  product_id      BIGINT       NOT NULL                     COMMENT '商品 ID',
  behavior_type   VARCHAR(20)  NOT NULL                     COMMENT '行为类型：VIEW/ADD_CART/FAVORITE/ORDER (PRD §4.3.3)',
  weight          INT          DEFAULT 1                    COMMENT '隐式反馈权重：浏览 1 / 加购 3 / 收藏 4 / 下单 5',
  extra           JSON         DEFAULT NULL                 COMMENT '附加信息（spec/quantity/orderId）',
  client_ip       VARCHAR(50)  DEFAULT NULL                 COMMENT '客户端 IP',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_product (product_id),
  KEY idx_type_time (behavior_type, created_at),
  CONSTRAINT fk_log_user FOREIGN KEY (user_id)    REFERENCES t_user (id)    ON DELETE SET NULL,
  CONSTRAINT fk_log_prod FOREIGN KEY (product_id) REFERENCES t_product (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为日志（推荐系统数据源，PRD §4.3.3）';

-- =============================================================================
-- 7. 后台：RBAC、轮播图、公告、操作日志
-- =============================================================================

CREATE TABLE t_admin (
  id              BIGINT       NOT NULL AUTO_INCREMENT     COMMENT '主键',
  username        VARCHAR(50)  NOT NULL                     COMMENT '用户名（唯一）',
  password        VARCHAR(100) NOT NULL                     COMMENT 'BCrypt 加密密码',
  email           VARCHAR(100) DEFAULT NULL                  COMMENT '邮箱',
  phone           VARCHAR(20)  DEFAULT NULL                  COMMENT '手机号',
  display_name    VARCHAR(50)  DEFAULT NULL                  COMMENT '显示名',
  status          TINYINT      DEFAULT 1                    COMMENT '状态 1启用 0禁用',
  last_login_at   DATETIME     DEFAULT NULL                  COMMENT '最近登录时间',
  last_login_ip   VARCHAR(50)  DEFAULT NULL                  COMMENT '最近登录 IP',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理员账号表 (PRD §3.1 ADMIN/OPERATOR)';

CREATE TABLE t_role (
  id          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  code        VARCHAR(50)  NOT NULL                         COMMENT '角色代码（如 ROLE_SUPER_ADMIN）',
  name        VARCHAR(50)  NOT NULL                         COMMENT '角色名称',
  description VARCHAR(255) DEFAULT NULL                     COMMENT '描述',
  status      TINYINT      DEFAULT 1                        COMMENT '状态 1启用 0禁用',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台角色表';

CREATE TABLE t_permission (
  id          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  code        VARCHAR(100) NOT NULL                         COMMENT '权限代码（如 product:read, order:ship）',
  name        VARCHAR(50)  NOT NULL                         COMMENT '权限名',
  module      VARCHAR(50)  NOT NULL                         COMMENT '所属模块（product/order/marketing/...）',
  description VARCHAR(255) DEFAULT NULL                     COMMENT '描述',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  KEY idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台权限点表';

CREATE TABLE t_admin_role (
  id          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  admin_id    BIGINT       NOT NULL                         COMMENT '管理员 ID',
  role_id     BIGINT       NOT NULL                         COMMENT '角色 ID',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_role (admin_id, role_id),
  KEY idx_admin (admin_id),
  KEY idx_role (role_id),
  CONSTRAINT fk_ar_admin FOREIGN KEY (admin_id) REFERENCES t_admin (id) ON DELETE CASCADE,
  CONSTRAINT fk_ar_role  FOREIGN KEY (role_id)  REFERENCES t_role (id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员-角色关联表';

CREATE TABLE t_role_permission (
  id            BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  role_id       BIGINT       NOT NULL                         COMMENT '角色 ID',
  permission_id BIGINT       NOT NULL                         COMMENT '权限 ID',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role_id, permission_id),
  KEY idx_role (role_id),
  CONSTRAINT fk_rp_role FOREIGN KEY (role_id)       REFERENCES t_role (id)       ON DELETE CASCADE,
  CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES t_permission (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

CREATE TABLE t_banner (
  id          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  title       VARCHAR(100) NOT NULL                         COMMENT '轮播图标题',
  image_url   VARCHAR(255) NOT NULL                         COMMENT '图片 URL',
  link_url    VARCHAR(255) DEFAULT NULL                     COMMENT '点击跳转链接',
  sort        INT          DEFAULT 0                       COMMENT '排序，数字越小越靠前',
  status      TINYINT      DEFAULT 1                        COMMENT '状态 1启用 0禁用',
  start_date  DATETIME     DEFAULT NULL                     COMMENT '生效开始时间',
  end_date    DATETIME     DEFAULT NULL                     COMMENT '生效结束时间',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_sort (sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播图表 (F-U-010)';

CREATE TABLE t_announcement (
  id          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  title       VARCHAR(100) NOT NULL                         COMMENT '公告标题',
  content     TEXT         NOT NULL                         COMMENT '公告内容',
  status      TINYINT      DEFAULT 1                        COMMENT '状态 1发布 0草稿',
  is_top      TINYINT      DEFAULT 0                        COMMENT '是否置顶',
  publish_at  DATETIME     DEFAULT NULL                     COMMENT '发布时间',
  expire_at   DATETIME     DEFAULT NULL                     COMMENT '过期时间',
  operator    VARCHAR(50)  DEFAULT NULL                     COMMENT '发布人',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台公告表 (F-A-063)';

CREATE TABLE t_operation_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  operator    VARCHAR(50)  NOT NULL                         COMMENT '操作员（admin / system）',
  event       TEXT         NOT NULL                         COMMENT '事件描述',
  module      VARCHAR(50)  DEFAULT NULL                     COMMENT '所属模块',
  ip          VARCHAR(50)  DEFAULT NULL                     COMMENT '操作 IP',
  user_agent  VARCHAR(255) DEFAULT NULL                     COMMENT '浏览器 UA',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_operator (operator),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台操作日志表 (F-C-007)';

-- =============================================================================
-- 28. 售后多 Agent 工单（电商客服 + 售后 + 风控）
-- =============================================================================

CREATE TABLE t_after_sale_ticket (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  ticket_no       VARCHAR(40)  NOT NULL                COMMENT '工单号',
  user_id         BIGINT       NOT NULL                COMMENT '用户 ID',
  order_id        BIGINT       NOT NULL                COMMENT '订单 ID',
  intent          VARCHAR(32)  DEFAULT NULL            COMMENT '意图：LOGISTICS/QUALITY/REFUND/MALICIOUS/OTHER',
  status          VARCHAR(24)  NOT NULL DEFAULT 'PENDING_REVIEW'
                  COMMENT 'PENDING_REVIEW/APPROVED/REJECTED/REFUNDED/CLOSED',
  risk_level      VARCHAR(16)  DEFAULT NULL            COMMENT 'LOW/MEDIUM/HIGH',
  risk_score      INT          DEFAULT 0               COMMENT '风控分 0-100',
  decision        VARCHAR(32)  DEFAULT NULL            COMMENT 'AUTO_APPROVE/MANUAL_REVIEW/REJECT',
  decision_reason VARCHAR(500) DEFAULT NULL            COMMENT '决策原因/命中规则',
  refund_amount   DECIMAL(10,2) DEFAULT NULL           COMMENT '退款金额',
  thread_id       VARCHAR(64)  DEFAULT NULL            COMMENT 'LangGraph thread_id，便于恢复',
  reply           TEXT         NULL                    COMMENT '给用户的回复',
  assignee        VARCHAR(50)  DEFAULT NULL            COMMENT '处理人',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket_no (ticket_no),
  KEY idx_ast_user (user_id),
  KEY idx_ast_order (order_id),
  KEY idx_ast_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后工单';

CREATE TABLE t_after_sale_event (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  ticket_id  BIGINT      NOT NULL                COMMENT '工单 ID',
  step       VARCHAR(40) NOT NULL                COMMENT '节点：INTENT/ORDER/RISK/POLICY/DECISION/REFUND/REPLY',
  actor      VARCHAR(50) DEFAULT 'agent'         COMMENT 'agent/system/admin',
  payload    TEXT        NULL                    COMMENT '节点输入输出 JSON（审计用）',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ase_ticket (ticket_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后工单 Agent 事件审计';

-- 恢复 FK 检查
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- DDL 结束
-- 29 张表全部建完：t_user / t_user_address / t_category / t_product /
--                  t_product_sku / t_product_image / t_product_review /
--                  t_cart_item / t_favorite / t_order / t_order_item /
--                  t_coupon / t_user_coupon / t_promotion /
--                  t_wallet_account / t_wallet_log / t_point_log / t_growth_log /
--                  user_behavior_log /
--                  t_admin / t_role / t_permission / t_admin_role / t_role_permission /
--                  t_banner / t_announcement / t_operation_log /
--                  t_after_sale_ticket / t_after_sale_event
-- =============================================================================