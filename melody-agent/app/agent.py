"""Agent 主循环：DeepSeek function-calling + 流式输出。

对一次用户输入，最多进行 max_tool_rounds 轮「LLM→工具→LLM」；
最终回答以 SSE 事件流形式产出：
  {"type":"delta","content":...}    流式文本
  {"type":"tool","name":...,"label":...} 工具状态提示
  {"type":"done","messageId":...}    结束
  {"type":"error","message":...}     出错
"""
import asyncio
import json
import logging

from openai import OpenAI

from . import session as sess
from . import store
from .config import settings
from .tools import TOOL_LABELS, TOOLS, ToolExecutor, _trim

log = logging.getLogger(__name__)

SYSTEM_PROMPT = """你是"麦乐迪零食商城"的智能购物助手，名叫"小乐"。你运行在商城网页右下角的聊天窗中。

## 你的能力
- 查商品：搜索、价格、库存、销量、评分、规格(SKU)详情
- 陪用户下单：查购物车→加入购物车→查地址/优惠券→展示订单摘要→用户确认后下单
- 查个人数据：订单、优惠券、账户余额/积分/等级（需用户已登录商城）
- 平台规则问答：优惠券怎么领/用、退款政策、会员等级、支付方式、购买须知等

## 铁律（必须遵守）
1. 价格、库存、订单、余额等一切实时数据必须调用工具查询，严禁编造或凭记忆回答。
2. 平台规则/政策/流程类问题必须先调用 search_knowledge 检索知识库，基于检索到的内容回答；检索不到就如实说明"知识库暂无该资料"，不要编造。
3. 下单流程：确认商品与规格数量 → add_to_cart → get_my_addresses / get_my_coupons → 调 place_order（不带确认）拿到摘要 → 原样完整展示摘要并明确询问用户是否确认 → 用户明确同意后带 user_confirmed=true 再次调用，参数不得改动。
4. 工具返回 {"error":"NEED_LOGIN"} 时，礼貌告知需先登录商城（页面右上角/个人中心登录），不要重复调用需登录的工具。
5. 金额用 ¥ 保留两位小数；回答简洁友好，用中文，不啰嗦；不要使用表格回答简单问题。
6. 用户只是闲聊或问与商城无关的问题时，简短友好回应并把话题引回购物。
7. 涉及支付：优先推荐钱包余额支付（即时完成）；用户选支付宝时提醒是沙箱环境、需到订单页完成付款。
"""


class AgentService:
    def __init__(self):
        self.executor = ToolExecutor()
        self.llm = OpenAI(api_key=settings.api_key or "EMPTY",
                          base_url=settings.base_url) if settings.api_key else None

    def ready(self) -> bool:
        return self.llm is not None

    # ---------- 供 HTTP 层调用的同步入口（在线程池中运行）----------

    def run(self, session_id: str, user_text: str, token: str | None,
            user_id: int | None, emit) -> None:
        """emit(event: dict) 由 HTTP 层提供，负责写入 SSE。"""
        if not self.llm:
            emit({"type": "error",
                  "message": "AI 服务未配置 DEEPSEEK_API_KEY，请在 melody-agent/.env 中填写后重启服务"})
            return

        st = sess.get_state(session_id)
        st.token = token
        st.user_id = user_id

        store.ensure_session(session_id, user_id)
        store.append_message(session_id, "user", user_text)

        # 组装上下文：system + 历史（滑动窗口）
        messages = [{"role": "system", "content": SYSTEM_PROMPT}]
        messages += store.load_context(session_id)[:-1]  # 刚 append 的本轮 user 消息去掉，末尾单独加
        messages.append({"role": "user", "content": user_text})

        final_text = ""
        try:
            final_text = self._agent_loop(session_id, st, messages, emit)
        except Exception as e:  # noqa: BLE001
            log.exception("[agent] loop failed")
            emit({"type": "error", "message": f"AI 服务异常：{e}"})
            final_text = final_text or "抱歉，我刚才出了点问题，请稍后再试。"

        if final_text:
            store.append_message(session_id, "assistant", final_text)
        emit({"type": "done"})

    def _agent_loop(self, session_id: str, st, messages: list[dict], emit) -> str:
        for _round in range(settings.max_tool_rounds):
            stream = self.llm.chat.completions.create(
                model=settings.model, messages=messages, tools=TOOLS, stream=True,
            )
            content_parts: list[str] = []
            tool_calls: dict[int, dict] = {}  # index -> {id,name,arguments}

            for chunk in stream:
                if not chunk.choices:
                    continue
                delta = chunk.choices[0].delta
                if delta and delta.content:
                    content_parts.append(delta.content)
                    emit({"type": "delta", "content": delta.content})
                if delta and delta.tool_calls:
                    for tc in delta.tool_calls:
                        acc = tool_calls.setdefault(tc.index, {"id": "", "name": "", "arguments": ""})
                        if tc.id:
                            acc["id"] = tc.id
                        if tc.function and tc.function.name:
                            acc["name"] = tc.function.name
                        if tc.function and tc.function.arguments:
                            acc["arguments"] += tc.function.arguments

            text = "".join(content_parts).strip()

            calls = [tool_calls[i] for i in sorted(tool_calls)]
            if not calls:
                return text  # 纯文本回答，结束

            # 有工具调用：记录 assistant tool_calls 消息（供审计落库），继续执行工具
            messages.append({
                "role": "assistant",
                "content": text or None,
                "tool_calls": [
                    {"id": c["id"], "type": "function",
                     "function": {"name": c["name"], "arguments": c["arguments"]}}
                    for c in calls
                ],
            })
            store.append_message(session_id, "assistant", text or None,
                                 tool_args=[{"name": c["name"], "arguments": c["arguments"]}
                                            for c in calls])

            for c in calls:
                name = c["name"]
                emit({"type": "tool", "name": name, "label": TOOL_LABELS.get(name, f"调用 {name}…")})
                try:
                    args = json.loads(c["arguments"] or "{}")
                except ValueError:
                    args = {}
                    result = {"error": "工具参数解析失败"}
                else:
                    result = self.executor.execute(name, args, st)
                # 截断后回填给 LLM 并落库审计
                result_text = _trim(result)
                messages.append({"role": "tool", "tool_call_id": c["id"],
                                 "content": result_text})
                store.append_message(session_id, "tool", None, tool_name=name,
                                     tool_args=args, tool_result=result)

        return "（本轮工具调用次数已达上限，请换个说法再试）"
