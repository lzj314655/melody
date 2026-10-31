"""售后政策 Agent 的轻量检索 + 确定性政策校验。

说明：这里先用「关键词/中文 bigram 检索」在 Markdown 政策库上召回条款，
保证离线可用；后续可以把 `search()` 换成现有 bge-small-zh 向量检索，
节点输出和决策逻辑不用改。
"""
from __future__ import annotations

import re
from datetime import datetime
from pathlib import Path

from ..config import settings

_KB_DIR = Path(__file__).resolve().parents[2] / "knowledge" / "after_sales"
_chunks: list[dict] | None = None

_QUALITY_WORDS = ("质量", "变质", "发霉", "过期", "异味", "异物", "破损", "坏了", "吃坏")
_EVIDENCE_WORDS = ("图片", "照片", "截图", "视频", "证据", "小票", "包装", "批次", "开箱")
_REFUND_WORDS = ("退款", "退货", "退钱", "赔偿", "补偿", "售后")
_LOGISTICS_WORDS = ("物流", "快递", "到哪", "没收到", "签收", "发货", "配送")


def _terms(text: str) -> set[str]:
    text = (text or "").lower()
    terms = set(re.findall(r"[a-z0-9]+", text))
    for run in re.findall(r"[\u4e00-\u9fff]+", text):
        if len(run) == 1:
            terms.add(run)
        for i in range(len(run) - 1):
            terms.add(run[i:i + 2])
    return terms


def _load_chunks() -> list[dict]:
    global _chunks
    if _chunks is not None:
        return _chunks
    chunks: list[dict] = []
    if _KB_DIR.is_dir():
        for path in sorted(_KB_DIR.glob("*.md")):
            text = path.read_text(encoding="utf-8")
            heading = path.stem
            for block in re.split(r"\n\s*\n", text):
                block = block.strip()
                if not block:
                    continue
                lines = block.split("\n", 1)
                if lines[0].startswith("#"):
                    heading = lines[0].lstrip("# ").strip()
                    block = lines[1].strip() if len(lines) > 1 else ""
                if len(block) < 12:
                    continue
                chunks.append({
                    "source": f"{path.name}#{heading}",
                    "text": block,
                })
    _chunks = chunks
    return chunks


def search(query: str, intent: str = "", top_k: int | None = None) -> list[dict]:
    """返回与问题最相关的政策段落，带来源，供回复引用。"""
    k = top_k or settings.after_sale_kb_top_k
    q = _terms(f"{query} {intent}")
    if not q:
        return []
    scored = []
    for chunk in _load_chunks():
        d = _terms(chunk["text"] + " " + chunk["source"])
        overlap = len(q & d)
        if overlap == 0:
            continue
        # 命中密度 + 意图词加权，避免长段落占优
        score = overlap / (len(q) ** 0.5) + (1.0 if intent and intent in chunk["source"] else 0.0)
        scored.append({**chunk, "score": round(score, 4)})
    scored.sort(key=lambda x: x["score"], reverse=True)
    return scored[:k]


def _parse_dt(value) -> datetime | None:
    if not value:
        return None
    s = str(value).replace("T", " ").split(".")[0]
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(s, fmt)
        except ValueError:
            continue
    return None


def _days_since(value) -> int | None:
    dt = _parse_dt(value)
    if not dt:
        return None
    return max(0, (datetime.now() - dt).days)


def evaluate(message: str, order: dict, intent: str) -> dict:
    """确定性政策判定。返回 ELIGIBLE / NEED_EVIDENCE / INELIGIBLE / MANUAL / ALREADY / INFO_ONLY。"""
    msg = (message or "").lower()
    status = (order or {}).get("status") or ""
    amount = float((order or {}).get("totalPrice") or 0)
    quality = intent == "QUALITY" or any(w in msg for w in _QUALITY_WORDS)
    evidence = any(w in msg for w in _EVIDENCE_WORDS)
    wants_refund = any(w in msg for w in _REFUND_WORDS)
    days = _days_since((order or {}).get("completedAt") or (order or {}).get("shippedAt"))

    result = "ELIGIBLE"
    reason = "符合售后受理条件"

    if status in ("REFUNDED", "CANCELED", "CLOSED"):
        result, reason = "INELIGIBLE", f"订单当前状态为 {status}，无法再次退款"
    elif status == "AFTER_SALE":
        result, reason = "ALREADY", "该订单已在售后处理中"
    elif status == "PENDING_PAY":
        result, reason = "INFO_ONLY", "订单尚未付款，可直接取消，无需退款"
    elif status == "PENDING_SHIP":
        result, reason = "MANUAL", "订单已付款但尚未发货，需要人工拦截后处理"
    elif status not in ("PENDING_RECEIVE", "COMPLETED"):
        result, reason = "MANUAL", f"订单状态 {status} 需要人工确认"
    elif intent == "LOGISTICS" and not wants_refund:
        result, reason = "INFO_ONLY", "物流查询类问题，不需要退款"
    elif intent == "OTHER" and not wants_refund:
        result, reason = "INFO_ONLY", "非退款诉求，转普通客服答复"
    elif days is not None and days > 7 and not quality:
        result, reason = "INELIGIBLE", f"已签收 {days} 天，超过 7 天无理由售后时效"
    elif quality and not evidence:
        result, reason = "NEED_EVIDENCE", "质量问题需补充商品照片/视频/批次等证据"
    elif quality:
        reason = "质量问题，符合退款/补发条件"
    elif days is not None and days <= 7:
        reason = f"签收 {days} 天，符合 7 天无理由条件"
    else:
        reason = "符合售后受理条件"

    citations = search(f"{message} {reason}", intent, settings.after_sale_kb_top_k)
    return {
        "result": result,
        "reason": reason,
        "amount": round(amount, 2),
        "daysSinceReceive": days,
        "hasEvidence": evidence,
        "qualityIssue": quality,
        "citations": citations,
    }
