"""售后多 Agent HTTP 接口（SSE + 工单查询）。"""
from __future__ import annotations

import asyncio
import json
import logging

from fastapi import APIRouter, Header, HTTPException, Query
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from ..backend import BackendError, MelodyBackendClient
from .graph import NODE_LABELS, get_graph

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/after-sales", tags=["after-sales"])
_backend = MelodyBackendClient()


class AfterSaleChatRequest(BaseModel):
    sessionId: str = Field(min_length=8, max_length=64)
    message: str = Field(min_length=1, max_length=2000)
    orderId: int | None = None
    orderNo: str | None = None


def _token(authorization: str | None) -> str | None:
    if authorization and authorization.startswith("Bearer "):
        return authorization[7:].strip()
    return None


def _auth_user(token: str | None) -> int:
    if not token:
        raise HTTPException(401, "请先登录商城后再申请售后")
    try:
        user = _backend.user_info(token)
    except BackendError as e:
        raise HTTPException(401, "登录状态已失效，请重新登录") from e
    uid = user.get("id")
    if uid is None:
        raise HTTPException(401, "登录状态已失效，请重新登录")
    return int(uid)


def _run_graph(session_id: str, message: str, token: str, user_id: int,
               order_id: int | None, order_no: str | None, emit) -> None:
    graph = get_graph()
    thread_id = f"{session_id}:{order_id or order_no or 'new'}"
    initial = {
        "session_id": session_id,
        "user_id": user_id,
        "token": token,
        "message": message,
        "order_id": order_id,
        "order_no": order_no,
        "trace": [],
    }
    config = {"configurable": {"thread_id": thread_id}}
    try:
        for update in graph.stream(initial, config, stream_mode="updates"):
            if not isinstance(update, dict):
                continue
            for node in update.keys():
                emit({"type": "agent", "node": node,
                      "label": NODE_LABELS.get(node, f"正在执行 {node}…")})
        final = graph.get_state(config).values
        reply = final.get("reply") or "抱歉，本次售后处理没有生成回复，请稍后重试。"
        emit({"type": "reply", "content": reply})
        emit({"type": "meta", "ticketId": final.get("ticket_id"),
              "ticketNo": final.get("ticket_no"),
              "decision": final.get("decision"),
              "risk": final.get("risk")})
    except Exception as e:  # noqa: BLE001
        log.exception("[after-sales] graph failed")
        emit({"type": "error", "message": f"售后处理异常：{e}"})
    finally:
        emit({"type": "done"})


@router.post("/chat")
async def after_sale_chat(req: AfterSaleChatRequest,
                          authorization: str | None = Header(default=None)):
    token = _token(authorization)
    user_id = _auth_user(token)
    queue: asyncio.Queue = asyncio.Queue()
    loop = asyncio.get_running_loop()

    def emit(event: dict) -> None:
        # graph 在线程池执行，必须线程安全地投递到事件循环
        loop.call_soon_threadsafe(queue.put_nowait, event)

    async def runner():
        try:
            await loop.run_in_executor(
                None, _run_graph,
                req.sessionId, req.message, token, user_id,
                req.orderId, req.orderNo, emit,
            )
        except Exception as e:  # noqa: BLE001
            log.exception("[after-sales] runner failed")
            emit({"type": "error", "message": f"售后处理异常：{e}"})
            emit({"type": "done"})

    async def sse():
        task = asyncio.create_task(runner())
        try:
            while True:
                event = await queue.get()
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n".encode("utf-8")
                if event.get("type") == "done":
                    break
        finally:
            if not task.done():
                task.cancel()
                try:
                    await task
                except (asyncio.CancelledError, Exception):  # noqa: BLE001
                    pass

    return StreamingResponse(sse(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache",
                                      "X-Accel-Buffering": "no"})


@router.get("/tickets/{ticket_id}")
async def get_ticket(ticket_id: int,
                     authorization: str | None = Header(default=None)):
    token = _token(authorization)
    user_id = _auth_user(token)
    try:
        ticket = _backend.after_sale_get_ticket(ticket_id)
    except BackendError as e:
        raise HTTPException(404, e.msg) from e
    if int(ticket.get("userId") or 0) != user_id:
        raise HTTPException(403, "无权查看该工单")
    return {"ticket": ticket}


@router.get("/health")
async def after_sale_health():
    return {"ok": True, "module": "after-sales", "graph": "langgraph"}
