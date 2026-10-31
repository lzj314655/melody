"""售后多 Agent 节点：意图、订单/物流、风控、政策、决策、执行、回复。

设计原则：
- 订单/物流、风控、政策校验尽量确定性；LLM 只负责理解和表达；
- 退款动作必须由决策层放行，且 Java 后端会再次校验订单状态；
- 每个节点把事实写入 state.trace，最终落 t_after_sale_event 审计。
"""
from __future__ import annotations

import json
import logging
import re
from datetime import datetime

from openai import OpenAI

from ..backend import BackendError, MelodyBackendClient
from ..config import settings
from . import policy

log = logging.getLogger(__name__)

_llm: OpenAI | None = None
_backend: MelodyBackendClient | None = None

NODE_LABELS = {
    "intent": "正在识别售后意图…",
    "order": "正在查询订单与物流…",
    "risk": "正在做风控评估…",
    "policy": "正在核对售后政策…",
    "decision": "正在生成处理决策…",
    "execute": "正在创建售后工单…",
    "reply": "正在生成回复…",
}

_ORDER_NO_RE = re.compile(r"ORD-\d{8}-\d{6,12}-\d{4}")


def _get_llm() -> OpenAI | None:
    global _llm
    if not settings.api_key:
        return None
    if _llm is None:
        _llm = OpenAI(api_key=settings.api_key, base_url=settings.base_url, timeout=15)
    return _llm


def _get_backend() -> MelodyBackendClient:
    global _backend
    if _backend is None:
        _backend = MelodyBackendClient()
    return _backend


def _trace(state: dict, step: str, payload: dict) -> None:
    state.setdefault("trace", []).append({"step": step, "payload": payload})


def _llm_json(system: str, user: str) -> dict | None:
    client = _get_llm()
    if not client:
        return None
    try:
        resp = client.chat.completions.create(
            model=settings.model,
            messages=[{"role": "system", "content": system},
                      {"role": "user", "content": user}],
            response_format={"type": "json_object"},
            temperature=0,
        )
        return json.loads(resp.choices[0].message.content or "{}")
    except Exception as e:  # noqa: BLE001
        log.warning("[after-sales] LLM intent failed: %s", e)
        return None


def _llm_text(system: str, user: str) -> str | None:
    client = _get_llm()
    if not client:
        return None
    try:
        resp = client.chat.completions.create(
            model=settings.model,
            messages=[{"role": "system", "content": system},
                      {"role": "user", "content": user}],
            temperature=0.3,
            max_tokens=500,
        )
        return (resp.choices[0].message.content or "").strip() or None
    except Exception as e:  # noqa: BLE001
        log.warning("[after-sales] LLM reply failed: %s", e)
        return None


# ============================ 1. 意图识别 ============================

def _rule_intent(message: str) -> str:
    m = message or ""
    if any(w in m for w in ("投诉", "骗子", "曝光", "12315", "起诉", "举报", "差评威胁")):
        return "MALICIOUS"
    if any(w in m for w in ("质量", "变质", "发霉", "过期", "异味", "异物", "吃坏", "破损")):
        return "QUALITY"
    if any(w in m for w in ("退款", "退货", "退钱", "售后")):
        return "REFUND"
    if any(w in m for w in ("物流", "快递", "到哪", "没收到", "签收", "发货", "配送")):
        return "LOGISTICS"
    return "OTHER"


def intent_node(state: dict) -> dict:
    message = state.get("message", "")
    order_no = state.get("order_no") or (_ORDER_NO_RE.search(message).group(0)
                                         if _ORDER_NO_RE.search(message) else None)
    rule = _rule_intent(message)
    data = _llm_json(
        "你是电商售后意图识别器。只输出 JSON，不要解释。"
        "intent 只能是 LOGISTICS/QUALITY/REFUND/MALICIOUS/OTHER；"
        "orderNo 是用户提到的订单号，没有则 null；"
        "reason 是简短原因；evidence 表示用户是否提供了照片/视频/截图等证据。",
        f"用户消息：{message}\n用户已选订单号：{order_no or '无'}",
    ) or {}

    intent = str(data.get("intent") or rule).upper()
    if intent not in ("LOGISTICS", "QUALITY", "REFUND", "MALICIOUS", "OTHER"):
        intent = rule
    # 关键词命中恶意/质量时，以规则为准，避免 LLM 把高风险文本降级成 OTHER
    if rule == "MALICIOUS":
        intent = "MALICIOUS"
    elif rule in ("QUALITY", "REFUND", "LOGISTICS") and intent == "OTHER":
        intent = rule
    slots = {
        "orderNo": data.get("orderNo") or order_no,
        "reason": data.get("reason") or "",
        "evidence": bool(data.get("evidence")),
        "urgency": data.get("urgency") or "MEDIUM",
    }
    _trace(state, "INTENT", {"intent": intent, "slots": slots, "ruleIntent": rule})
    return {"intent": intent, "slots": slots, "order_no": slots["orderNo"]}


# ============================ 2. 订单 / 物流 ============================

def _build_logistics(order: dict) -> list[dict]:
    status = order.get("status")
    tracking = order.get("trackingNo") or "暂无"
    shipped = order.get("shippedAt")
    if status in ("PENDING_PAY", "PENDING_SHIP"):
        return [{"status": "待发货", "description": "商家尚未发货", "time": order.get("paidAt") or ""}]
    events = [{"status": "已发货", "description": f"物流单号 {tracking}", "time": shipped or ""}]
    if status in ("PENDING_RECEIVE", "COMPLETED", "AFTER_SALE", "REFUNDED"):
        events.append({"status": "运输中", "description": "包裹已交由承运商运输", "time": shipped or ""})
    if status in ("COMPLETED", "AFTER_SALE", "REFUNDED"):
        events.append({"status": "已签收", "description": "包裹已签收", "time": order.get("completedAt") or ""})
    return events


def order_node(state: dict) -> dict:
    token = state.get("token")
    if not token:
        return {"error": "请先登录商城后再申请售后", "order": {}}
    backend = _get_backend()
    try:
        if state.get("order_id"):
            order = backend.order_detail(token, int(state["order_id"]))
        elif state.get("order_no"):
            order = backend.order_detail_by_no(token, str(state["order_no"]))
        else:
            return {"error": "请提供订单号（例如 ORD-20261030-123456789-0001）", "order": {}}
    except BackendError as e:
        return {"error": f"订单查询失败：{e.msg}", "order": {}}

    product_id = None
    items = order.get("items") or []
    if items:
        product_id = items[0].get("productId")
    safe_order = {
        "id": order.get("id"),
        "orderNo": order.get("orderNo"),
        "status": order.get("status"),
        "totalPrice": order.get("totalPrice"),
        "payMethod": order.get("payMethod"),
        "trackingNo": order.get("trackingNo"),
        "paidAt": order.get("paidAt"),
        "shippedAt": order.get("shippedAt"),
        "completedAt": order.get("completedAt"),
        "refundReason": order.get("refundReason"),
        "items": [{"productId": i.get("productId"), "productName": i.get("productName"),
                   "quantity": i.get("quantity"), "subtotal": i.get("subtotal")}
                  for i in items],
    }
    logistics = _build_logistics(order)
    _trace(state, "ORDER", {"orderNo": safe_order["orderNo"], "status": safe_order["status"],
                            "logistics": logistics})
    return {
        "order": safe_order,
        "order_id": order.get("id"),
        "product_id": product_id,
        "logistics": logistics,
        "error": None,
    }


# ============================ 3. 风控 ============================

def risk_node(state: dict) -> dict:
    user_id = state.get("user_id")
    if not user_id:
        return {"risk": {"score": 50, "level": "MEDIUM", "reasons": ["未识别到登录用户"]}}
    try:
        risk = _get_backend().after_sale_risk(int(user_id), state.get("product_id"))
    except Exception as e:  # noqa: BLE001
        log.warning("[after-sales] risk features failed: %s", e)
        risk = {"score": 50, "level": "MEDIUM",
                "reasons": ["风控数据暂不可用，按中风险转人工"]}
    _trace(state, "RISK", {"score": risk.get("score"), "level": risk.get("level"),
                           "reasons": risk.get("reasons")})
    return {"risk": risk}


# ============================ 4. 政策 ============================

def policy_node(state: dict) -> dict:
    order = state.get("order") or {}
    if not order:
        return {"policy": {"result": "INFO_ONLY", "reason": "缺少订单信息，无法做政策判定",
                           "citations": []}}
    result = policy.evaluate(state.get("message", ""), order, state.get("intent", "OTHER"))
    _trace(state, "POLICY", {"result": result.get("result"), "reason": result.get("reason"),
                             "citations": [c["source"] for c in result.get("citations", [])]})
    return {"policy": result}


# ============================ 5. 决策 ============================

def decision_node(state: dict) -> dict:
    order = state.get("order") or {}
    if state.get("error") or not order:
        decision = "NEED_ORDER"
        reason = state.get("error") or "缺少订单信息"
    else:
        intent = state.get("intent", "OTHER")
        policy_result = (state.get("policy") or {}).get("result", "MANUAL")
        risk = state.get("risk") or {}
        risk_level = str(risk.get("level") or "MEDIUM").upper()
        amount = float((state.get("policy") or {}).get("amount") or order.get("totalPrice") or 0)
        reasons = []
        if policy_result in ("INELIGIBLE",):
            decision, reason = "REJECT", (state.get("policy") or {}).get("reason", "不符合售后政策")
        elif policy_result in ("ALREADY", "INFO_ONLY"):
            decision, reason = "INFO_ONLY", (state.get("policy") or {}).get("reason", "无需退款")
        elif intent == "MALICIOUS":
            decision, reason = "MANUAL_REVIEW", "投诉/恶意风险，需人工复核"
        elif risk_level == "HIGH":
            decision = "MANUAL_REVIEW"
            reasons = risk.get("reasons") or []
            reason = "高风险工单转人工" + ("：" + "；".join(reasons[:3]) if reasons else "")
        elif policy_result in ("NEED_EVIDENCE", "MANUAL"):
            decision = "MANUAL_REVIEW"
            reason = (state.get("policy") or {}).get("reason", "需人工复核")
        elif amount > 200:
            decision = "MANUAL_REVIEW"
            reason = f"退款金额 ￥{amount:.2f} 超过自动放行阈值 ￥200"
        else:
            decision = "AUTO_APPROVE"
            reason = "低风险 + 政策可退 + 金额在自动放行阈值内"
    _trace(state, "DECISION", {"decision": decision, "reason": reason})
    return {"decision": decision, "decision_reason": reason}


# ============================ 6. 执行（工单/退款） ============================

def execute_node(state: dict) -> dict:
    decision = state.get("decision")
    if decision in ("INFO_ONLY", "NEED_ORDER"):
        return {"ticket": None, "ticket_status": None}

    order = state.get("order") or {}
    risk = state.get("risk") or {}
    policy_data = state.get("policy") or {}
    payload = {
        "userId": state.get("user_id"),
        "orderId": order.get("id"),
        "intent": state.get("intent"),
        "status": "PENDING_REVIEW",
        "riskLevel": risk.get("level"),
        "riskScore": risk.get("score"),
        "decision": decision,
        "decisionReason": state.get("decision_reason"),
        "refundAmount": policy_data.get("amount") or order.get("totalPrice"),
        "threadId": state.get("session_id"),
    }
    backend = _get_backend()
    try:
        ticket = backend.after_sale_create_ticket(payload)
    except Exception as e:  # noqa: BLE001
        log.exception("[after-sales] create ticket failed")
        return {"ticket": None, "ticket_status": None,
                "error": f"工单创建失败：{e}"}

    ticket_id = ticket.get("id")
    state["ticket_id"] = ticket_id
    state["ticket_no"] = ticket.get("ticketNo")

    # 把所有节点事实写审计事件
    for item in state.get("trace", []):
        try:
            backend.after_sale_event(ticket_id, item["step"], "agent",
                                     json.dumps(item["payload"], ensure_ascii=False)[:2000])
        except Exception:  # noqa: BLE001
            log.warning("[after-sales] append event failed step=%s", item.get("step"))

    ticket_status = "PENDING_REVIEW"
    if decision == "AUTO_APPROVE":
        try:
            refunded = backend.after_sale_refund(ticket_id)
            ticket_status = refunded.get("status") or "REFUNDED"
        except Exception as e:  # noqa: BLE001
            log.warning("[after-sales] auto refund failed: %s", e)
            try:
                backend.after_sale_event(ticket_id, "REFUND", "system",
                                         f"auto refund failed: {e}")
            except Exception:  # noqa: BLE001
                pass
            return {"ticket": ticket, "ticket_id": ticket_id,
                    "ticket_no": ticket.get("ticketNo"), "ticket_status": "PENDING_REVIEW",
                    "decision": "MANUAL_REVIEW",
                    "decision_reason": f"自动退款失败，已转人工：{e}"}
    elif decision == "REJECT":
        try:
            updated = backend.after_sale_update_ticket(ticket_id, {
                "status": "REJECTED", "decision": "REJECT",
                "decisionReason": state.get("decision_reason"),
            })
            ticket_status = updated.get("status") or "REJECTED"
        except Exception:  # noqa: BLE001
            log.warning("[after-sales] mark rejected failed ticket=%s", ticket_id)

    return {"ticket": ticket, "ticket_id": ticket_id,
            "ticket_no": ticket.get("ticketNo"), "ticket_status": ticket_status}


# ============================ 7. 回复 ============================

def _template_reply(state: dict) -> str:
    decision = state.get("decision")
    order = state.get("order") or {}
    ticket_no = state.get("ticket_no")
    policy_data = state.get("policy") or {}
    citations = policy_data.get("citations") or []
    cite = f"\n\n依据：{citations[0]['source']}" if citations else ""

    if decision == "NEED_ORDER":
        return "为了帮您处理售后，请提供订单号（在「我的订单」里可以看到，例如 ORD-20261030-123456789-0001）。"
    if decision == "INFO_ONLY":
        if order:
            lines = [f"您的订单 {order.get('orderNo')} 当前状态：{order.get('status')}。"]
            if order.get("trackingNo"):
                lines.append(f"物流单号：{order.get('trackingNo')}。")
            lines.append(policy_data.get("reason") or "如有退款/退货诉求，请直接告诉我。")
            return "\n".join(lines)
        return policy_data.get("reason") or "请提供订单号，我帮您查询。"
    if decision == "AUTO_APPROVE" and state.get("ticket_status") == "REFUNDED":
        return (f"已为您通过退款，退款金额 ￥{policy_data.get('amount', order.get('totalPrice'))}，"
                f"将按原支付方式退回。工单号：{ticket_no}。{cite}")
    if decision == "REJECT":
        return (f"很抱歉，本次售后暂时无法通过：{state.get('decision_reason')}。"
                f"工单号：{ticket_no}。如您有新的照片/视频等证据，可以补充后申诉。{cite}")
    if decision == "MANUAL_REVIEW":
        return (f"已收到您的问题，因需要进一步核实，已为您转人工审核。"
                f"工单号：{ticket_no or '处理中'}，客服会在 24 小时内联系您。{cite}")
    return f"已收到您的问题，工单号：{ticket_no or '处理中'}，我们会尽快处理。"


def reply_node(state: dict) -> dict:
    base = _template_reply(state)
    # 让 LLM 只做话术润色，事实和结论由模板/规则给定
    order = state.get("order") or {}
    prompt = (
        "你是电商售后客服。请基于给定事实写一段 80-180 字的中文回复，"
        "不要新增政策承诺，不要改变结论，不要编造物流。\n"
        f"用户消息：{state.get('message')}\n"
        f"决策：{state.get('decision')}\n"
        f"决策原因：{state.get('decision_reason')}\n"
        f"订单状态：{order.get('status')}\n"
        f"退款金额：{(state.get('policy') or {}).get('amount')}\n"
        f"工单号：{state.get('ticket_no')}\n"
        f"模板回复：{base}"
    )
    text = _llm_text("你是严谨、克制的电商售后客服。", prompt) or base
    _trace(state, "REPLY", {"decision": state.get("decision"), "ticketNo": state.get("ticket_no")})
    # 回写工单回复，后台详情可直接看到 Agent 草稿
    if state.get("ticket_id"):
        try:
            _get_backend().after_sale_update_ticket(
                int(state["ticket_id"]), {"reply": text})
        except Exception as e:  # noqa: BLE001
            log.warning("[after-sales] save reply failed: %s", e)
    return {"reply": text}


# ============================ 路由 ============================

def route_intent(state: dict) -> str:
    # 售后类意图即使没给订单号，也走 order 节点拿到明确的“请提供订单号”提示
    if state.get("intent") in ("LOGISTICS", "QUALITY", "REFUND", "MALICIOUS"):
        return "order"
    if state.get("order_id") or state.get("order_no"):
        return "order"
    if _ORDER_NO_RE.search(state.get("message", "")):
        return "order"
    return "reply"


def route_decision(state: dict) -> str:
    if state.get("decision") in ("INFO_ONLY", "NEED_ORDER"):
        return "reply"
    return "execute"
