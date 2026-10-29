# UAT 验收清单 — 答辩前必过

> 适用对象:答辩评委演示 + 自测
> 通过标准:**所有 P0 用例必须通过**;P1 允许最多 1 个失败;P2 不阻塞但要登记
> 一次跑完整流程,大约需要 30~45 分钟

---

## 1. 验收前置(检查表)

| # | 项 | 命令 / 操作 | 通过 |
|---|---|---|---|
| U-PRE-01 | MySQL 通 | `mysqladmin ping -uroot -p123456` | PONG |
| U-PRE-02 | Redis 通 | `redis-cli ping` | PONG |
| U-PRE-03 | DB 已初始化 | `SELECT COUNT(*) FROM t_product` ≥ 50 | ≥ 50 |
| U-PRE-04 | 图片已迁移 | `SELECT COUNT(*) FROM t_product WHERE cover_image LIKE 'http%'` | = 0 |
| U-PRE-05 | 后端启动 | `curl -I http://localhost:8080/api/product/list` | 200 |
| U-PRE-06 | 前端启动 | 浏览器开 `http://localhost:3000` | 看到商品列表 |
| U-PRE-07 | 9 张抽样图 | 跑 9-id curl 抽样脚本 | 9/9 全 200 |
| U-PRE-08 | 至少有 1 个 user + 1 个 admin 账号 | 见 `data-init.sql` 默认账号 | 存在 |

---

## 2. 业务流验收(答辩演示流程)

### 场景 A:会员购物流(20 分钟)

| # | 步骤 | 截图位置 | 通过 |
|---|---|---|---|
| U-A-01 | 浏览器开 `http://localhost:3000` 看到首页商品瀑布 | `evidence/U-A-01/` | ☐ |
| U-A-02 | 点击任一商品进详情,主图 + SKU + 评价都显示 | `evidence/U-A-02/` | ☐ |
| U-A-03 | 切换 SKU 价格正确变化 | — | ☐ |
| U-A-04 | 点收藏图标变实心 | `evidence/U-A-04/` | ☐ |
| U-A-05 | 点"加入购物车" → 跳购物车 | `evidence/U-A-05/` | ☐ |
| U-A-06 | 选 2 件 + 选 1 张有效券 | `evidence/U-A-06/` | ☐ |
| U-A-07 | 显示结算金额 = 总价 - 优惠 | — | ☐ |
| U-A-08 | 点"去结算" → 创建订单 | `evidence/U-A-08/` | ☐ |
| U-A-09 | 跳订单详情页,商品图正常 | `evidence/U-A-09/` | ☐ |
| U-A-10 | 进"我的订单" → 看到刚下的订单 | `evidence/U-A-10/` | ☐ |
| U-A-11 | 进钱包 → 余额减少,流水有 `-` | `evidence/U-A-11/` | ☐ |
| U-A-12 | 进"我的收藏" → 之前收藏的商品在 | `evidence/U-A-12/` | ☐ |
| U-A-13 | 在订单详情页"确认收货" → 状态变已完成 | `evidence/U-A-13/` | ☐ |
| U-A-14 | 提交评价 → 详情页评价区出现 | `evidence/U-A-14/` | ☐ |

### 场景 B:管理员后台(15 分钟)

| # | 步骤 | 截图位置 | 通过 |
|---|---|---|---|
| U-B-01 | `/admin/login` 登录 | `evidence/U-B-01/` | ☐ |
| U-B-02 | Dashboard 6 个图表/数字有数据 | `evidence/U-B-02/` | ☐ |
| U-B-03 | `/admin/products` 列表分页 | `evidence/U-B-03/` | ☐ |
| U-B-04 | 新建商品 → 列表出现 | `evidence/U-B-04/` | ☐ |
| U-B-05 | `/admin/categories` CRUD | `evidence/U-B-05/` | ☐ |
| U-B-06 | `/admin/orders` 发货 | `evidence/U-B-06/` | ☐ |
| U-B-07 | `/admin/coupons` 新建一张满减券 | `evidence/U-B-07/` | ☐ |
| U-B-08 | 切回前台用 user 账号 → 能领到刚才的券 | `evidence/U-B-08/` | ☐ |
| U-B-09 | `/admin/users` 启用/停用一个用户 | `evidence/U-B-09/` | ☐ |
| U-B-10 | `/admin/manage` 创建管理员 | `evidence/U-B-10/` | ☐ |
| U-B-11 | `/admin/settings` → 重置 demo 数据 → 前台确认回到 init 状态 | `evidence/U-B-11/` | ☐ |

### 场景 C:安全与异常(10 分钟)

| # | 步骤 | 期望 | 通过 |
|---|---|---|---|
| U-C-01 | 未登录访问 `/cart` | 跳 `/login?redirect=/cart` | ☐ |
| U-C-02 | 篡改 localStorage token | 再操作需登录的接口 → 跳登录 | ☐ |
| U-C-03 | user token 直接输 `/admin` | 跳 `/admin/login` | ☐ |
| U-C-04 | 后台操作时 admin token 过期 | 自动跳 `/admin/login` | ☐ |
| U-C-05 | 余额不足时下单价订单 | 提示"余额不足",不创建 | ☐ |
| U-C-06 | 同一订单点两次"确认收货" | 第二次拒绝 | ☐ |
| U-C-07 | OPERATOR 调 SUPER_ADMIN 专属接口 | 403 | ☐ |
| U-C-08 | 退出登录后再访问 `/profile` | 跳登录 | ☐ |

### 场景 D:行为埋点 + 推荐(5 分钟)

| # | 步骤 | 验证 | 通过 |
|---|---|---|---|
| U-D-01 | 滚动首页 30 秒 | `user_behavior_log` 有 VIEW 记录 | ☐ |
| U-D-02 | 加购 3 件不同商品 | `user_behavior_log` 有 3 条 ADD_CART | ☐ |
| U-D-03 | 收藏 2 个商品 | 2 条 FAVORITE | ☐ |
| U-D-04 | 提交 1 条评价 | 1 条 REVIEW,extra 含 rating | ☐ |
| U-D-05 | 结算 1 次 | 1 条 ORDER | ☐ |
| U-D-06 | 进首页看"为你推荐"位 | 有 ≥ 3 个推荐商品 | ☐ |
| U-D-07 | 商品详情页底部"相似商品" | 有 ≥ 2 个 | ☐ |

---

## 3. 答辩演示脚本(2 分钟精简版)

评委最关心的演示路径,5 步讲清:

1. **首页浏览** — 展示商品瀑布、轮播图、分类导航,点开任一商品
2. **商品详情** — 展示 SKU 切换、收藏、加购、商品图(本地图片代理)
3. **购物车 + 结算** — 选 2 件 + 1 张券,展示优惠计算,完成下单
4. **订单详情** — 展示订单状态、商品图、确认收货、申请退款、提交评价
5. **后台 Dashboard** — 切到 admin 账号,展示 6 个 KPI 数据图表,演示发货/退款审批

每个步骤截 1 张图作为答辩 PPT 素材。

---

## 4. 验收通过判据

| 项 | 标准 |
|---|---|
| 场景 A | 14/14 全过 |
| 场景 B | 11/11 全过 |
| 场景 C | 8/8 全过(允许 U-C-04 失败,只要后端日志无报错) |
| 场景 D | 7/7 全过 |
| 整体 | P0 全过 + 无 Sev1 缺陷 + 无已知技术债导致崩溃 |