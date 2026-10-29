"""FastAPI 入口：
- POST /api/chat          SSE 流式对话（Header 可带商城用户 JWT）
- GET  /api/chat/history  拉取会话历史（含工具调用记录）
- POST /api/chat/new      新开对话（前端换一个 sessionId 即可，此接口仅落一个标记）
- GET  /api/health        健康检查
"""
import asyncio
import base64
import json
import logging
import re
import uuid
from collections.abc import AsyncIterator

from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from . import db, store
from .agent import AgentService
from .config import settings
from .knowledge import get_kb

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("melody-agent")

app = FastAPI(title="麦乐迪智能购物助手", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins or ["*"],
    # 本地开发无论用 localhost 还是 127.0.0.1、无论哪个端口都放行
    allow_origin_regex=r"^https?://(localhost|127\.0\.0\.1)(:\d+)?$",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

agent = AgentService()

# JWT payload 按 `.` 分段 base64url 解码，取 sub/userId（不验签——鉴权由商城后端负责）
_B64_RE = re.compile(r"^[\w\-]*$")


def parse_jwt_user(token: str | None) -> int | None:
    if not token:
        return None
    try:
        payload_part = token.split(".")[1]
        padded = payload_part + "=" * (-len(payload_part) % 4)
        payload = json.loads(base64.urlsafe_b64decode(padded))
        for key in ("userId", "uid", "sub", "id"):
            v = payload.get(key)
            if v is not None:
                return int(v)
    except Exception:  # noqa: BLE001
        pass
    return None


class ChatRequest(BaseModel):
    sessionId: str = Field(min_length=8, max_length=64)
    message: str = Field(min_length=1, max_length=2000)


@app.on_event("startup")
async def startup():
    try:
        db.ensure_tables()
    except Exception as e:  # noqa: BLE001
        log.error("[startup] MySQL 初始化失败（聊天记录将不可用）：%s", e)


@app.get("/api/health")
async def health():
    return {
        "ok": True,
        "llm": "configured" if agent.ready() else "missing DEEPSEEK_API_KEY",
        "kb_chunks": _kb_count(),
    }


def _kb_count() -> int | None:
    try:
        return get_kb().count()
    except Exception:  # noqa: BLE001
        return None


@app.post("/api/chat")
async def chat(req: ChatRequest, authorization: str | None = Header(default=None)):
    if not agent.ready():
        raise HTTPException(503, "DEEPSEEK_API_KEY 未配置")
    token = authorization[7:].strip() if authorization and authorization.startswith("Bearer ") else None
    user_id = parse_jwt_user(token)
    session_id = req.sessionId
    queue: asyncio.Queue = asyncio.Queue()

    def emit(event: dict):
        queue.put_nowait(event)

    loop = asyncio.get_running_loop()

    async def runner():
        await loop.run_in_executor(None, agent.run, session_id, req.message, token, user_id, emit)
        await queue.put(None)  # 结束标记

    async def sse() -> AsyncIterator[bytes]:
        task = asyncio.create_task(runner())
        try:
            while True:
                event = await queue.get()
                if event is None:
                    break
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n".encode("utf-8")
        finally:
            if not task.done():
                task.cancel()
                try:
                    await task
                except (asyncio.CancelledError, Exception):  # noqa: BLE001
                    pass

    return StreamingResponse(sse(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})


@app.get("/api/chat/history")
async def history(sessionId: str = Query(min_length=8), limit: int = Query(100, le=500)):
    try:
        return {"messages": store.load_history(sessionId, limit)}
    except Exception as e:  # noqa: BLE001
        log.exception("[history] load failed")
        raise HTTPException(500, f"历史加载失败：{e}") from e


@app.post("/api/chat/new")
async def new_session():
    """返回一个新的 sessionId；旧会话记录仍保留在 MySQL 可随时查看。"""
    return {"sessionId": uuid.uuid4().hex}
