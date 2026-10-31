"""售后 LangGraph 冒烟测试（不依赖 LLM / 后端 / MySQL）。

运行：
    python scripts/after_sales_smoke.py

用 FakeBackend 替换 HTTP 客户端，验证：
1. 低风险 + 政策可退 → AUTO_APPROVE → 工单 REFUNDED
2. 高风险 → MANUAL_REVIEW，不自动退款
3. 没有订单号 → NEED_ORDER，提示补订单号
4. 政策不可退（超 7 天且非质量问题）→ REJECT
"""
import os
import sys
from pathlib import Path

# 必须赶在 app.config 读取 .env 之前置空，避免 smoke 触发真实 LLM 调用
os.environ["DEEPSEEK_API_KEY"] = ""
os.environ.setdefault("AFTER_SALE_INTERNAL_TOKEN", "smoke-token")

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from app.after_sales import nodes  # noqa: E402
from app.after_sales.graph import build_graph  # noqa: E402


class FakeBackend:
    def __init__(self, risk_level="LOW"):
        self.risk_level = risk_level
        self.refunded = False
        self.ticket = None
        self.events = []

    def order_detail(self, token, order_id):
        return {
            "id": order_id,
            "orderNo": "ORD-20261030-123456789-0001",
            "status": "COMPLETED",
            "totalPrice": 59.90,
            "payMethod": "WALLET",
            "trackingNo": "SF123",
            "completedAt": "2026-10-30 10:00:00",
            "items": [{"productId": 100, "productName": "坚果", "quantity": 1, "subtotal": 59.90}],
        }

    def order_detail_by_no(self, token, order_no):
        return self.order_detail(token, 1)

    def after_sale_risk(self, user_id, product_id=None):
        if self.risk_level == "HIGH":
            return {"score": 85, "level": "HIGH", "reasons": ["90 天退款率 80%"]}
        return {"score": 10, "level": "LOW", "reasons": []}

    def after_sale_create_ticket(self, payload):
        self.ticket = {"id": 1, "ticketNo": "AS-20261030-0001", **payload}
        return self.ticket

    def after_sale_event(self, ticket_id, step, actor, payload):
        self.events.append((step, actor, payload))

    def after_sale_update_ticket(self, ticket_id, payload):
        self.ticket.update(payload)
        return self.ticket

    def after_sale_refund(self, ticket_id):
        self.refunded = True
        self.ticket["status"] = "REFUNDED"
        return self.ticket


def run_case(message, risk_level="LOW", order_id=1):
    fake = FakeBackend(risk_level)
    nodes._backend = fake
    nodes._llm = None
    graph = build_graph()
    state = {
        "session_id": "smoke-session-0001",
        "user_id": 1,
        "token": "fake-token",
        "message": message,
        "order_id": order_id,
        "trace": [],
    }
    final = graph.invoke(state, {"configurable": {"thread_id": f"smoke-{message[:6]}-{risk_level}"}})
    return final, fake


def main():
    # 1) 质量问题 + 有证据 + 低风险 → 自动退款
    final, fake = run_case("商品发霉了，我有照片，要求退款", "LOW")
    assert final["decision"] == "AUTO_APPROVE", final.get("decision")
    assert final["ticket_status"] == "REFUNDED", final.get("ticket_status")
    assert fake.refunded is True
    assert fake.events, "应有 Agent 事件审计"
    print("[1] AUTO_APPROVE -> REFUNDED OK")

    # 2) 高风险 → 转人工
    final, fake = run_case("我要退款", "HIGH")
    assert final["decision"] == "MANUAL_REVIEW", final.get("decision")
    assert fake.refunded is False
    print("[2] HIGH risk -> MANUAL_REVIEW OK")

    # 3) 没订单号 → 提示补订单号
    final, fake = run_case("我要退款", "LOW", order_id=None)
    assert final["decision"] == "NEED_ORDER", final.get("decision")
    assert "订单号" in final["reply"]
    print("[3] missing order -> NEED_ORDER OK")

    # 4) 超 7 天非质量问题 → 拒绝
    fake = FakeBackend("LOW")
    nodes._backend = fake
    fake.order_detail = lambda token, order_id: {
        **FakeBackend.order_detail(fake, token, order_id),
        "completedAt": "2026-09-01 10:00:00",
    }
    graph = build_graph()
    final = graph.invoke({
        "session_id": "smoke-session-0004", "user_id": 1, "token": "fake-token",
        "message": "不想要了，退款", "order_id": 1, "trace": [],
    }, {"configurable": {"thread_id": "smoke-case-4"}})
    assert final["decision"] == "REJECT", final.get("decision")
    print("[4] out of policy -> REJECT OK")

    print("AFTER_SALES_SMOKE_OK")


if __name__ == "__main__":
    main()
