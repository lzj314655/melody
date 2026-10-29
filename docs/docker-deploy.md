# Docker 本地部署指南

一键把整套系统（前端 nginx + Spring Boot 后端 + MySQL + Redis + AI 助手）跑进容器。

## 前置条件

- Docker Desktop 已安装并启动（`docker info` 能输出 Server Version）
- Windows 下宿主机 3306/6379 若被本机 MySQL/Redis 占用**没关系**——容器映射的是 3307/6380，互不冲突

## 首次部署

```bash
# 1. 准备环境变量（DeepSeek Key 必填，用于 AI 助手）
cp .env.docker.example .env.docker
#    编辑 .env.docker 填入 DEEPSEEK_API_KEY

# 2. 构建并启动（首次约 5-15 分钟，下载依赖 + 编译）
docker compose --env-file .env.docker up -d --build

# 3. 查看状态（等全部 healthy）
docker compose ps

# 4. 访问
#    前台商城/后台管理:  http://localhost:3000
#    后台管理入口:       http://localhost:3000/admin/login（admin / admin123）
#    AI 助手:            页面右下角悬浮球（同源 /agent 反代，无跨域）
#    后端直连:            http://localhost:8081（宿主机 8080 若被其他项目占用可改 compose 映射）
#    容器 MySQL:          localhost:3307（root / 123456）
#    容器 Redis:          localhost:6380
```

首次启动时 MySQL 容器自动执行 `backend/sql/` 下的建库脚本 + 演示数据 + AI 聊天表，无需手工导库。

## 日常操作

```bash
docker compose ps                      # 状态
docker compose logs -f backend         # 看某服务日志（backend/frontend/agent/mysql）
docker compose restart backend         # 重启某服务
docker compose down                    # 停止（数据保留在 mysql-data 卷）
docker compose down -v                 # 停止并清空数据（下次 up 重新导演示数据）
docker compose up -d --build backend   # 只重建某服务（如改了后端代码）
```

## 与裸机开发的关系

| | 裸机版（原方式） | Docker 版 |
|---|---|---|
| 前端 | `npm run dev` → :3000 | 容器 :3000（二选一，端口冲突时先停另一个） |
| 后端 | IDEA/mvn 跑 → :8080 | 容器 :8080 |
| MySQL | 本机 :3306 | 容器 :3307 |
| Redis | 本机 :6379 | 容器 :6380 |

两套完全隔离：Docker 版用容器内的 MySQL/Redis（首次从 SQL 初始化），不影响本机已有的开发数据。vite dev 模式仍直连本机裸机后端（默认 `http://localhost:8080/api`）。

## 常见问题

- **8080 端口被裸机后端占用**：先停掉裸机后端（IDEA 里 Stop）再 `docker compose up -d`，或临时改 compose 中 backend 的映射为 `"8081:8080"`（支付宝回调地址需同步改环境变量 `ALIPAY_NOTIFY_URL`）
- **AI 助手回复"未配置 Key"**：`.env.docker` 里 DEEPSEEK_API_KEY 没填，填好后 `docker compose up -d --force-recreate agent`
- **图片 404**：确认仓库根目录 `image/` 文件夹存在（compose 会挂载进后端容器）
- **首次构建 Maven/npm 慢**：Dockerfile 内已配阿里云/npmmirror 镜像源，国内网络一般 5-15 分钟
- **改了前端代码重新构建**：`docker compose up -d --build frontend`

## 工程化说明

- 后端配置全部外部化：`application.yml` 用 `${ENV:默认值}` 占位，容器内外同一份代码
- 前端 API 地址构建期注入（`VITE_API_BASE`/`VITE_AGENT_BASE`），本地默认不变
- 健康检查 + `depends_on: condition: service_healthy` 保证启动顺序（MySQL 就绪后才起后端）
- 数据持久化：`mysql-data` 卷（业务数据）、`agent-data` 卷（向量模型缓存 + 向量库）
