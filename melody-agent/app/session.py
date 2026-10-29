"""活跃会话运行时状态（内存）：保存转发 token、下单确认上下文。"""
import time
from dataclasses import dataclass, field


@dataclass
class SessionState:
    token: str | None = None
    user_id: int | None = None
    # 下单护栏：place_order 未确认被拒时记录订单签名；确认后必须一致才放行
    pending_order_sig: tuple | None = None
    pending_order_summary: dict | None = None
    updated_at: float = field(default_factory=time.time)


_sessions: dict[str, SessionState] = {}
_MAX_SESSIONS = 500


def get_state(session_id: str) -> SessionState:
    st = _sessions.get(session_id)
    if st is None:
        if len(_sessions) >= _MAX_SESSIONS:
            # 简单淘汰：最久未活跃的一半（演示规模足够）
            for k in sorted(_sessions, key=lambda k: _sessions[k].updated_at)[: _MAX_SESSIONS // 2]:
                _sessions.pop(k, None)
        st = SessionState()
        _sessions[session_id] = st
    st.updated_at = time.time()
    return st


def order_signature(cart_item_ids: list[int], address_id: int) -> tuple:
    return (tuple(sorted(cart_item_ids)), address_id)
