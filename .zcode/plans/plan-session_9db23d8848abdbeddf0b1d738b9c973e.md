# 麦乐迪商城 Docker 工程化部署方案

## 架构（docker-compose 一键编排，5 个服务）

```
宿主机 localhost:3000  →  nginx (frontend 容器)
                          ├─ 静态文件 (Vue 构建产物)
                          ├─ /api/**  ──▶ backend:8080   (Spring Boot)
                          ├─ /img/**  ──▶ backend:8080   (商品图片, 挂载 ./image)
                          └─ /agent/** ─▶ agent:8100     (Python AI, 重写为 /api/**)
backend ──▶ mysql:3306 (容器内) + redis:6379 (容器内) + 挂载 ./image:/app/image
agent ──▶ mysql (聊天表) + backend (工具调用) + DeepSeek (外网)
宿主机 8080 同时映射 backend（支付宝沙箱回调地址保持 localhost:8080 可用）
MySQL 宿主机映射 3307、Redis 6380（避开本机已在跑的 3306/6379，不冲突）
```

## 一、新增文件

| 文件 | 作用 |
|------|------|
| `backend/Dockerfile` | 多阶段：maven:3.9-temurin-17 构建（阿里云镜像源、跳测试）→ temurin-17-jre 运行；环境变量外部化（DB/Redis/图片根路径） |
| `backend/.dockerignore` | 排除 target/ |
| `frontend_google1/Dockerfile` | node:20 构建（npmmirror 源）→ nginx:alpine 托管 dist |
| `frontend_google1/nginx.conf` | 静态托管 + `/api`、`/img` 反代 backend + `/agent` 反代 agent（SSE 关闭缓冲） |
| `frontend_google1/.dockerignore` | 排除 node_modules/dist |
| `melody-agent/Dockerfile` | python:3.11-slim；构建期下载 int8 向量模型（hf-mirror），启动时向量库缺失自动 `build_kb.py` |
| `melody-agent/.dockerignore` | 排除 venv/data |
| `docker-compose.yml`（根目录） | 5 服务 + healthcheck + depends_on 条件等待 + 数据卷 |
| `.env.docker.example` | MYSQL_ROOT_PASSWORD / DEEPSEEK_API_KEY 等集中配置 |
| `docs/docker-deploy.md` | 部署文档（构建/启动/访问/常见问题） |

## 二、代码小改（配置外部化，工程化核心，行为不变）

1. `frontend_google1/src/api/http.js`：`baseURL: import.meta.env.VITE_API_BASE || "http://localhost:8080/api"` —— 本地 dev 行为不变；Docker 构建注入 `VITE_API_BASE=/api`（同源走 nginx 反代）
2. `frontend_google1/src/api/agent.js`：`AGENT_BASE = import.meta.env.VITE_AGENT_BASE || "http://localhost:8100"`；Docker 注入 `/agent`
3. `frontend_google1/src/components/AdminLayout.vue`：SSE 地址同样取 `VITE_API_BASE` 前缀（修复硬编码）
4. `backend/src/main/resources/application.yml`：MySQL/Redis 地址、alipay.returnUrl/notifyUrl 加 `${ENV:默认值}` 占位符 —— 本地跑不变，容器内用环境变量覆盖
5. melody-agent 的 config.py 本来就全读环境变量，无需改

## 三、数据库初始化

compose 挂载现有 SQL 到 mysql `/docker-entrypoint-initdb.d/`（按序号命名）：
`01-schema.sql` → `02-data-init.sql` → `03-order-alipay-columns.sql` → `04-insert-50-new-products.sql` → `05-wallet-recharge-order.sql` → `06-migrate-to-local-images.sql` → `07-cleanup-duplicate-coupons.sql` → `08-chat-tables.sql`（来自 melody-agent/sql）
首次 `up` 自动建库导入演示数据；数据持久化在 named volume `mysql-data`。

## 四、实施步骤

1. 检查本机 Docker Desktop 可用（未装则先装/提示启动）
2. 写 4 个 Dockerfile + .dockerignore + nginx.conf
3. 改 3 个前端文件的 baseURL 外部化 + application.yml 占位符
4. 写 docker-compose.yml + .env.docker.example + 部署文档
5. `docker compose up -d --build` 全量构建启动
6. 验证：`localhost:3000` 前台商城可浏览（图片走 nginx 反代）、登录（走容器 DB）、后台大屏有数据、AI 助手对话（用现有 DeepSeek Key）、`docker compose ps` 全部 healthy
7. 本地原有服务不受影响（3306/6379/8080 照旧可跑裸机版）

## 明确不做

- CI/CD（GitHub Actions）——需要时后续加
- 镜像推送到仓库——纯本地部署
- HTTPS/域名——本地演示