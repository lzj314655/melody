<div align="center">

# 🍿 麦乐迪零食购物商场系统

**Melody Savory Shop** — 基于 Spring Boot 3 + Vue 3 的前后端分离垂直零食电商，
集成 **个性化推荐（ItemCF）** 与 **DeepSeek 智能购物助手（Function Calling + RAG）** 两大亮点模块

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![DeepSeek](https://img.shields.io/badge/DeepSeek-Function%20Calling-4D6BFE)](https://platform.deepseek.com/)
[![FastAPI](https://img.shields.io/badge/FastAPI-AI%20Agent-009688?logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/Use-毕业设计-8B5CF6)](#-免责声明)

[快速开始](#-快速开始) · [系统架构](#-系统架构) · [功能总览](#-功能总览) · [AI 智能助手](#-ai-智能购物助手) · [项目结构](#-项目结构) · [测试](#-测试)

**前台商城** · 实拍截图

![前台商城首页](docs/screenshots/home.png)

**智能购物助手（小乐）** · DeepSeek 实时对话 + 商品检索

![AI 智能购物助手](docs/screenshots/ai-assistant.png)

**管理后台 · 数据大屏（深色模式）** · 左右两张为同一系统的双主题形态

![数据大屏](docs/screenshots/admin-dashboard.png)

</div>

---

## 📑 目录

- [✨ 功能总览](#-功能总览)
- [🏗️ 系统架构](#️-系统架构)
- [🚀 快速开始](#-快速开始)
  - [方式一：Docker 一键部署（推荐）](#方式一docker-一键部署推荐)
  - [方式二：本地裸机开发](#方式二-本地裸机开发)
- [🔑 演示账号](#-演示账号)
- [🤖 AI 智能购物助手](#-ai-智能购物助手)
- [📸 界面速览](#-界面速览)
- [📁 项目结构](#-项目结构)
- [🧪 测试](#-测试)
- [🛠️ 技术栈明细](#️-技术栈明细)
- [⚠️ 免责声明](#️-免责声明)

---

## ✨ 功能总览

**前台商城（用户端）**

| 模块 | 说明 |
|------|------|
| 🛍️ 商品浏览 | 分类导航 / 关键词搜索 / 多维排序 / SKU 规格选择 / 详情轮播与评价 |
| 🛒 购物车 | 增删改查、勾选结算、库存实时校验 |
| 📦 订单中心 | 下单（余额 / 支付宝沙箱双通道）、取消、确认收货、退款申请、订单跟踪 |
| 🎫 营销体系 | 优惠券领取 / 核销（满减 + 折扣双类型，终身限领一次）、会员等级（V0–V3） |
| 💰 钱包中心 | 余额支付 / 充值 / 退款到账 / 流水明细 |
| ⭐ 商品评价 | 仅购买过可评价、一人一评防重、评分自动回写商品 |
| 🎯 个性化推荐 | 基于浏览 / 加购 / 购买行为的 **ItemCF 协同过滤**，Redis 缓存 + 定时预热 |
| 🤖 AI 购物助手 | 右下角悬浮客服：查价 / 加购 / 下单 / 规则问答（RAG），见[下文](#-ai-智能购物助手) |

**管理后台（Admin）**

| 模块 | 说明 |
|------|------|
| 📊 数据大屏 | GMV / 转化率 / 复购率等 6 项 KPI + 月度走势 + 订单趋势 + TOP10（深浅双主题） |
| 📦 商品管理 | 上下架 / 多规格 SKU 独立定价 / 本机图片库选图 |
| 🧾 订单管理 | 全状态筛选、一键发货、退款审核、订单详情时间线 |
| 👥 用户管理 | 会员等级筛选、赠送积分、冻结 / 解锁 |
| 📣 营销管理 | 优惠券创建 / 停用 / 删除，实时剩余量 |
| 🔐 系统管理 | RBAC 管理员账号、操作日志审计、新订单 SSE 实时弹窗 |

---

## 🏗️ 系统架构

```mermaid
flowchart LR
    subgraph client["👤 浏览器"]
        FE["Vue 3 SPA<br/>前台商城 + 管理后台 + AI 聊天窗"]
    end

    subgraph nginx["🔀 Nginx :3000"]
        NG["静态托管<br/>/api → backend · /agent → agent · /img → backend"]
    end

    subgraph backend["☕ Spring Boot 3 :8080"]
        API["REST API<br/>JWT + RBAC 鉴权"]
        RECO["推荐引擎<br/>ItemCF + Redis 缓存"]
        PAY["支付宝沙箱<br/>支付 / 主动查询"]
    end

    subgraph agent["🤖 FastAPI Agent :8100"]
        LOOP["DeepSeek 对话循环<br/>Function Calling × 12 工具"]
        RAG["RAG 知识库<br/>int8 BGE 向量检索"]
    end

    DB[("🐬 MySQL 8<br/>业务 30+ 表 + 聊天记录")]
    RD[("⚡ Redis 7<br/>推荐缓存 / 会话")]
    DS["🌐 DeepSeek API"]

    FE --> NG
    NG -->|"/api, /img"| API
    NG -->|"/agent (SSE 流式)"| LOOP
    API --> DB
    API --> RD
    API --> PAY
    LOOP -->|"工具调用(转发 JWT)"| API
    LOOP --> RAG
    LOOP <--> DS
    RAG -->|"向量库 data/vecstore.npz"| RD
```

**AI 助手下单安全护栏**：`place_order` 未带 `user_confirmed` 时服务端实时组装订单摘要并拒绝执行，
LLM 须向用户完整展示摘要、获得确认后原参数重调，且购物车项 + 地址签名不一致仍拒绝——杜绝大模型幻觉下单。

---

## 🚀 快速开始

### 方式一：Docker 一键部署（推荐）

> 前置：Docker Desktop 已启动。首次构建约 5–15 分钟（已配国内镜像源）。

```bash
# 1. 准备环境变量（填入 DeepSeek API Key，AI 助手用）
cp .env.docker.example .env.docker
#    编辑 .env.docker → DEEPSEEK_API_KEY=sk-xxxx

# 2. 构建并启动全部 5 个容器（MySQL 首次启动自动建库 + 导入演示数据）
docker compose --env-file .env.docker up -d --build

# 3. 查看状态，等全部 healthy
docker compose ps

# 4. 打开商城
#    前台/后台:  http://localhost:3000
#    后台入口:   http://localhost:3000/admin/login
```

> 详细说明（端口占用、数据重置、常见问题）见 [docs/docker-deploy.md](docs/docker-deploy.md)

### 方式二：本地裸机开发

<details>
<summary><b>展开步骤</b>（JDK 17+ / Maven / Node 18+ / MySQL 8 / Redis 7）</summary>

```bash
# 1. 数据库：创建 melody_shop 库后按序导入
mysql -u root -p < backend/sql/schema.sql
mysql -u root -p melody_shop < backend/sql/data-init.sql
mysql -u root -p melody_shop < backend/sql/order-alipay-columns.sql
mysql -u root -p melody_shop < backend/sql/insert-50-new-products.sql
mysql -u root -p melody_shop < backend/sql/wallet-recharge-order.sql
mysql -u root -p melody_shop < backend/sql/migrate-to-local-images.sql

# 2. 后端（会自动读取 gitignored 的 application-local.yml 中的本地密码/支付宝密钥）
cd backend && mvn spring-boot:run        # :8080

# 3. AI 助手
cd melody-agent
python -m venv venv && venv\Scripts\pip install -r requirements.txt
copy .env.example .env                   # 填入 DEEPSEEK_API_KEY
venv\Scripts\python scripts\build_kb.py  # 构建 RAG 向量库（首次自动下载 24MB 模型）
venv\Scripts\python run.py               # :8100

# 4. 前端
cd frontend_google1 && npm install && npm run dev   # :3000
```

</details>

---

## 🔑 演示账号

| 端 | 账号 | 密码 | 说明 |
|----|------|------|------|
| 前台用户 | `13800138001` | `password` | V1 会员，含历史订单 / 收货地址 |
| 前台用户 | `13800138002` | `password` | V3 会员，多订单演示 |
| 后台管理员 | `admin` | `admin123` | 超级管理员，全部权限 |
| 后台管理员 | `operator` | `password` | 运营管理员 |

> 支付宝为**沙箱环境**，沙箱买家账号需在 [open.alipay.com](https://open.alipay.com) 沙箱控制台查看。

---

## 🤖 AI 智能购物助手

右下角悬浮窗「小乐」，基于 **DeepSeek Chat + Function Calling + RAG** 的三能力 Agent：

| 能力 | 说明 |
|------|------|
| 🔍 商品查询 | `search_products` / `get_product_detail`——价格、库存、SKU 实时查询，严禁编造 |
| 🛒 代客下单 | 加购 → 选地址 / 优惠券 → **展示订单摘要 → 用户确认 → 下单**（三重护栏见架构说明） |
| 📋 数据查询 | 订单 / 优惠券 / 钱包余额 / 积分（转发用户 JWT，以本人身份调用商城 API） |
| 📚 规则问答 | 优惠券规则 / 退款政策 / 会员等级等 6 篇知识文档向量化入库，回答**强制先检索**，附来源 |

技术要点：会话上下文 MySQL 持久化（含全部工具调用链审计）、SSE 流式输出、滑动窗口上下文、
向量模型 `bge-small-zh-v1.5 int8`（本地 ONNX 推理，免 API Key）。

## 📸 界面速览

| 商品列表 | 管理后台 · 商品管理 |
|---|---|
| ![商品列表](docs/screenshots/products.png) | ![后台商品管理](docs/screenshots/admin-products.png) |

---

## 📁 项目结构

```
melody/
├── backend/                 # Spring Boot 3 后端
│   ├── src/main/java/com/melody/
│   │   ├── controller/      # 用户端 + 管理端 REST 接口
│   │   ├── service/         # 业务逻辑（订单/优惠券/推荐 ItemCF/钱包…）
│   │   ├── mapper/          # MyBatis-Plus 数据层
│   │   └── config/          # Security/JWT/支付宝/推荐预热
│   ├── sql/                 # 建库脚本 + 演示数据（容器自动导入）
│   └── Dockerfile           # 多阶段构建（阿里云镜像源）
├── frontend_google1/        # Vue 3 前端（前台 + 管理后台同工程）
│   ├── src/views/frontend/  # 商城页面
│   ├── src/views/admin/     # 后台页面（含数据大屏）
│   ├── src/components/ChatWidget.vue   # AI 助手聊天窗
│   ├── nginx.conf           # 容器内反代配置
│   └── Dockerfile           # node 构建 → nginx 托管
├── melody-agent/            # Python AI 智能助手
│   ├── app/                 # agent 循环 / 工具集 / RAG / MySQL 持久化
│   ├── knowledge/           # 6 篇平台规则知识文档（RAG 语料）
│   ├── scripts/             # 建库 / 冒烟测试
│   └── Dockerfile
├── 项目测试/                 # 独立测试工程（L1 API / L2 鉴权矩阵 / L3 集成 / L4 UAT）
├── docs/                    # 部署文档 + 截图
├── topic/                   # PRD（SRS）与选题分析
├── image/                   # 商品图片（/img/** 静态资源源目录）
└── docker-compose.yml       # 5 服务一键编排
```

## 🧪 测试

独立测试工程 [`项目测试/`](项目测试/README.md)，四层覆盖：

| 层级 | 范围 | 通过标准 |
|------|------|----------|
| L1 API | 48 端点 happy path | 全过 |
| L2 鉴权矩阵 | 5 角色 × 5 端点 | 全过 |
| L3 集成 | 并发 / 降级 / 注入 | 全过 |
| L4 UAT | 4 大业务场景 | 全过 |

后端另有 JUnit 5 + H2 单元/集成测试（`backend/src/test`），前端 `npm run build` 校验。

## 🛠️ 技术栈明细

**后端** Spring Boot 3.2.5 · Spring Security + JWT · MyBatis-Plus · MySQL 8 · Redis 7 · 支付宝 SDK（沙箱）· SSE
**前端** Vue 3.4 (Composition API) · Vite 5 · Pinia · Vue Router · Tailwind CSS 4 · lucide 图标
**AI 服务** Python 3.11 · FastAPI · openai SDK (DeepSeek) · onnxruntime (bge-small-zh int8) · NumPy 向量检索 · PyMySQL
**部署** Docker Compose（nginx / spring / fastapi / mysql / redis，健康检查 + 按序启动）

## ⚠️ 免责声明

> 本项目为**计算机专业本科毕业设计作品，纯属学术研究用途**。
> 「麦乐迪」为虚构命名，不代表任何真实商业主体；商品图片、价格均为模拟数据；
> 支付、物流等第三方服务使用**沙箱环境或 Mock**，不产生真实交易。
