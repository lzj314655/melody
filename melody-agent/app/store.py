"""会话/消息持久化（MySQL t_chat_session / t_chat_message）+ LLM 上下文装配。

落库策略：
- user / assistant 文本消息：role + content
- assistant 的工具调用：role='assistant'，content 可能为空，tool_args 存完整 tool_calls JSON（审计用）
- 工具执行结果：role='tool'，tool_name + tool_args + tool_result

LLM 上下文回放策略：只回放 user / assistant 的文本消息（工具中间过程不回放，
最终回答已包含工具结论），用滑动窗口控制 token。
"""
import json
import logging
import random

from . import db
from .config import settings

log = logging.getLogger(__name__)


def ensure_session(session_id: str, user_id: int | None) -> None:
    row = db.query("SELECT id, user_id FROM t_chat_session WHERE session_id=%s", (session_id,))
    if not row:
        db.execute(
            "INSERT INTO t_chat_session (session_id, user_id) VALUES (%s, %s)",
            (session_id, user_id),
        )
    elif user_id is not None and row[0]["user_id"] != user_id:
        db.execute("UPDATE t_chat_session SET user_id=%s WHERE session_id=%s",
                   (user_id, session_id))


def append_message(session_id: str, role: str, content: str | None = None,
                   tool_name: str | None = None, tool_args: dict | list | None = None,
                   tool_result: dict | list | None = None) -> None:
    args_json = json.dumps(tool_args, ensure_ascii=False) if tool_args is not None else None
    result_json = json.dumps(tool_result, ensure_ascii=False) if tool_result is not None else None
    db.execute(
        "INSERT INTO t_chat_message (session_id, role, content, tool_name, tool_args, tool_result) "
        "VALUES (%s, %s, %s, %s, %s, %s)",
        (session_id, role, content, tool_name, args_json, result_json),
    )
    db.execute("UPDATE t_chat_session SET updated_at=NOW() WHERE session_id=%s", (session_id,))
    # 抽样裁剪：单会话超过上限时删最旧消息
    if random.random() < 0.05:
        rows = db.query(
            "SELECT COUNT(*) AS c FROM t_chat_message WHERE session_id=%s", (session_id,))
        if rows and rows[0]["c"] > settings.session_keep_messages + 50:
            db.execute(
                "DELETE FROM t_chat_message WHERE session_id=%s AND id <= ("
                "  SELECT id FROM (SELECT id FROM t_chat_message WHERE session_id=%s "
                "  ORDER BY id DESC LIMIT 1 OFFSET %s) t)",
                (session_id, session_id, settings.session_keep_messages - 1),
            )


def load_context(session_id: str, limit: int | None = None) -> list[dict]:
    """LLM 上下文：最近 N 条 user/assistant 文本消息（按时间正序）。"""
    limit = limit or settings.context_window
    rows = db.query(
        "SELECT role, content FROM t_chat_message "
        "WHERE session_id=%s AND role IN ('user','assistant') AND content IS NOT NULL "
        "ORDER BY id DESC LIMIT %s",
        (session_id, limit),
    )
    msgs = [{"role": r["role"], "content": r["content"]} for r in reversed(rows)]
    return msgs


def load_history(session_id: str, limit: int = 100) -> list[dict]:
    """前端历史渲染：含工具调用记录（时间正序，最多最近 limit 条）。"""
    rows = db.query(
        "SELECT role, content, tool_name, tool_args, tool_result, created_at "
        "FROM t_chat_message WHERE session_id=%s ORDER BY id DESC LIMIT %s",
        (session_id, limit),
    )
    out = []
    for r in reversed(rows):
        item = {"role": r["role"], "content": r["content"], "time": str(r["created_at"])}
        if r["role"] == "tool":
            args = _safe_json(r["tool_args"])
            result = _safe_json(r["tool_result"])
            item["tool"] = r["tool_name"]
            item["args"] = args
            item["result"] = result
        out.append(item)
    return out


def _safe_json(text: str | None):
    if not text:
        return None
    try:
        return json.loads(text)
    except ValueError:
        return text
