"""LangGraph 售后工单状态。

所有节点只通过 state 传递事实，不直接互调；便于单测和回放。
"""
from typing import Any, TypedDict


class AfterSaleState(TypedDict, total=False):
    # 请求上下文
    session_id: str
    user_id: int
    token: str
    message: str
    order_id: int | None
    order_no: str | None

    # 各 Agent 输出
    intent: str
    slots: dict[str, Any]
    order: dict[str, Any]
    product_id: int | None
    logistics: list[dict[str, Any]]
    risk: dict[str, Any]
    policy: dict[str, Any]

    # 决策与结果
    decision: str
    decision_reason: str
    ticket: dict[str, Any] | None
    ticket_id: int | None
    ticket_no: str | None
    ticket_status: str | None
    reply: str
    error: str | None

    # 审计轨迹：每个节点追加一项
    trace: list[dict[str, Any]]
