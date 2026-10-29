# melody-agent — 麦乐迪智能购物助手（Python Agent）

商城右下角的 AI 智能客服：查商品价格、帮用户加购下单、订单/优惠券查询、基于 RAG 知识库的规则问答；会话上下文 MySQL 持久化，重开窗口不丢。

```
Vue 前端(3000) ──SSE──▶ melody-agent(8100) ──HTTP──▶ Spring Boot 商城后端(8080)
                          │  DeepSeek function-calling（对话+工具调用）
                          │  int8 ONNX bge-small-zh（本地向量化）
                          │  numpy 向量库（data/vecstore.npz）
                          └─ MySQL melody_shop.t_chat_session/t_chat_message
```

## 一次性准备

```bash
cd melody-agent
python -m venv venv
venv\Scripts\pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/
copy .env.example .env     # 编辑 .env，填入 DEEPSEEK_API_KEY（必填）
python scripts\build_kb.py # 构建 RAG 知识库（自动下载 int8 向量模型，约24MB）
```

- DeepSeek API Key：注册 https://platform.deepseek.com 获取
- MySQL 复用商城库（root/123456/melody_shop），聊天表 `sql/chat-tables.sql` 服务启动时自动建
- 验证码/账号：冒烟测试用 `.env` 里的 `SMOKE_*`（默认 13800138001 / password）

## 启动（三个服务都要开）

```bash
# 1) 商城后端（Java，见 backend/）
# 2) AI 助手服务
cd melody-agent && venv\Scripts\python run.py     # 0.0.0.0:8100
# 3) 商城前端
cd frontend_google1 && npm run dev                # :3000，右下角出现 AI 悬浮球
```

## 测试

```bash
venv\Scripts\python scripts\smoke_test.py   # 16 项冒烟：后端链路/工具/下单护栏/RAG/MySQL
curl http://localhost:8100/api/health       # {"ok":true,"llm":"configured","kb_chunks":33}
```

## 目录说明

| 路径 | 说明 |
|------|------|
| `app/main.py` | FastAPI：`POST /api/chat`(SSE)、`GET /api/chat/history`、`/api/chat/new`、`/api/health` |
| `app/agent.py` | Agent 循环：DeepSeek function-calling，流式输出，最多 8 轮工具调用 |
| `app/tools.py` | 12 个工具定义与执行器；`place_order` 强制用户确认（服务端组装摘要 + 签名校验） |
| `app/knowledge.py` | RAG：int8 ONNX 向量模型 + numpy 余弦检索；`rebuild()` 重建向量库 |
| `app/backend.py` | 商城后端 HTTP 客户端（R 包装解析、BackendError） |
| `app/db.py / store.py` | MySQL 连接与消息落库；LLM 上下文滑动窗口（最近 20 条） |
| `knowledge/*.md` | 知识库源文档（购买须知/优惠券/会员/退款/支付/FAQ），改后重跑 build_kb.py |
| `data/` | 向量库 + 向量模型缓存（gitignore） |

## 设计要点

- **身份**：前端把商城登录 JWT 随聊天请求转发，AI 以当前用户身份调后端接口，不落密码
- **下单护栏**：`place_order` 未带 `user_confirmed` 时服务端实时组装订单摘要并拒绝执行；LLM 须向用户展示摘要、获得确认后原参数重调；签名（购物车项+地址）不一致仍拒绝
- **RAG**：规则类问题强制先检索知识库；知识文档内容对照后端代码实际逻辑编写
- **持久化**：会话/消息（含每次工具调用入参与结果）全部落 MySQL，可审计；前端 localStorage 存 sessionId，重开自动恢复
- **低内存适配**：向量化用 int8 量化模型 + batch=1 + 关闭 ONNX arena，适配 16GB 高负载演示机

## 常见问题

- **pip 装包 403**：清华镜像偶发拒绝 pip UA，换阿里源（见上）或官方源
- **模型下载慢/失败**：已默认走 hf-mirror.com，可用环境变量 `HF_ENDPOINT` 覆盖
- **ONNX 初始化 bad allocation**：机器提交内存紧张，代码已内置 6 次退避重试；仍失败就关闭部分应用（IDEA/浏览器标签）后重试
- **`llm: missing DEEPSEEK_API_KEY`**：.env 没填 Key，仅影响对话，工具链与 RAG 不依赖 LLM
