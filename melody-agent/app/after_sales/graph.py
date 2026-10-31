"""LangGraph 售后工单编排。

START → intent → order → (risk ∥ policy) → decision → execute → reply → END

- order / risk / policy 都是只读节点，可并行；
- decision 是确定性规则，不由 LLM 决定；
- execute 创建工单并按决策调用 Java 内部退款接口；
- 高风险工单停在 PENDING_REVIEW，由后台人工审核。
"""
from __future__ import annotations

import logging

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

from .nodes import (
    NODE_LABELS,
    decision_node,
    execute_node,
    intent_node,
    order_node,
    policy_node,
    reply_node,
    risk_node,
    route_decision,
    route_intent,
)
from .state import AfterSaleState

log = logging.getLogger(__name__)

_graph = None


def build_graph():
    g = StateGraph(AfterSaleState)

    g.add_node("intent", intent_node)
    g.add_node("order", order_node)
    g.add_node("risk", risk_node)
    g.add_node("policy", policy_node)
    g.add_node("decision", decision_node)
    g.add_node("execute", execute_node)
    g.add_node("reply", reply_node)

    g.add_edge(START, "intent")
    g.add_conditional_edges("intent", route_intent, {
        "order": "order",
        "reply": "reply",
    })
    g.add_edge("order", "risk")
    g.add_edge("order", "policy")
    g.add_edge("risk", "decision")
    g.add_edge("policy", "decision")
    g.add_conditional_edges("decision", route_decision, {
        "execute": "execute",
        "reply": "reply",
    })
    g.add_edge("execute", "reply")
    g.add_edge("reply", END)

    # MVP 用内存 checkpointer；工单本身已落 MySQL，重启后仍可查。
    # 生产可换 Redis/Postgres checkpointer，实现跨实例恢复。
    return g.compile(checkpointer=MemorySaver())


def get_graph():
    global _graph
    if _graph is None:
        _graph = build_graph()
    return _graph


__all__ = ["build_graph", "get_graph", "NODE_LABELS"]
