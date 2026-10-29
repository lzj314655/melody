"""Agent 工具定义（OpenAI function-calling schema）+ 执行器。

所有工具同步执行（backend 为同步 httpx），由 agent 在线程池中调用。
需登录的工具在无 token 时返回友好错误，由 LLM 引导用户登录。
"""
import json
import logging

from . import session as sess
from .backend import BackendError, MelodyBackendClient
from .knowledge import get_kb

log = logging.getLogger(__name__)

# 每个工具给前端的中文状态提示（SSE tool 事件展示）
TOOL_LABELS = {
    "search_products": "正在搜索商品…",
    "get_product_detail": "正在查看商品详情…",
    "get_categories": "正在获取分类…",
    "search_knowledge": "正在检索平台知识库…",
    "get_user_info": "正在查询账户信息…",
    "get_my_cart": "正在查看购物车…",
    "add_to_cart": "正在加入购物车…",
    "get_my_addresses": "正在查看收货地址…",
    "get_my_coupons": "正在查看优惠券…",
    "place_order": "正在提交订单…",
    "get_my_orders": "正在查询订单…",
    "cancel_order": "正在取消订单…",
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "search_products",
            "description": "按关键词搜索商城商品，返回名称、价格、库存、销量、评分等。回答商品价格/有没有某商品时必须调用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "搜索关键词，如 牛肉干、辣条"},
                    "sort": {"type": "string", "enum": ["default", "price-asc", "price-desc", "sales-desc", "rating-desc"],
                             "description": "排序：default综合/price-asc价格升/price-desc价格降/sales-desc销量/rating-desc评分"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_product_detail",
            "description": "查商品详情和全部规格(SKU)的精确价格与库存。加购/下单前必须调用拿到 skuId。",
            "parameters": {
                "type": "object",
                "properties": {"productId": {"type": "integer", "description": "商品ID"}},
                "required": ["productId"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_categories",
            "description": "获取商城商品分类列表。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_knowledge",
            "description": "检索平台官方知识库（购买须知、优惠券规则、会员等级、退款政策、支付方式、FAQ）。凡涉及平台规则/政策/流程的问题必须先调用本工具，基于检索结果回答。",
            "parameters": {
                "type": "object",
                "properties": {"query": {"type": "string", "description": "检索问题，如 优惠券怎么用"}},
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_user_info",
            "description": "查询当前登录用户的昵称、等级、成长值、积分、钱包余额。需用户已登录商城。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_my_cart",
            "description": "查看当前用户购物车商品列表。需登录。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "add_to_cart",
            "description": "把商品某规格加入当前用户购物车。需先通过 get_product_detail 拿到 productId 和 skuId。需登录。",
            "parameters": {
                "type": "object",
                "properties": {
                    "productId": {"type": "integer"},
                    "skuId": {"type": "integer"},
                    "quantity": {"type": "integer", "description": "数量，至少1"},
                },
                "required": ["productId", "skuId", "quantity"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_my_addresses",
            "description": "查看当前用户收货地址列表（下单要用 addressId）。需登录。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_my_coupons",
            "description": "查看当前用户优惠券。status 可选：可用/已用/过期/ALL，默认只看可用。需登录。",
            "parameters": {
                "type": "object",
                "properties": {"status": {"type": "string", "enum": ["可用", "已用", "过期", "ALL"]}},
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "place_order",
            "description": (
                "提交订单（先加购再下单）。必须先向用户展示订单摘要并获得用户明确同意后，"
                "携带 user_confirmed=true 调用；否则会被拒绝并返回摘要。payMethod: WALLET 钱包即时支付(默认) / "
                "ALIPAY 支付宝沙箱(生成待付款订单,用户需自行在订单页完成支付)。需登录。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "cartItemIds": {"type": "array", "items": {"type": "integer"},
                                    "description": "购物车项ID列表（来自 get_my_cart）"},
                    "addressId": {"type": "integer", "description": "收货地址ID"},
                    "userCouponId": {"type": "integer", "description": "使用的用户优惠券ID，不用传 null"},
                    "payMethod": {"type": "string", "enum": ["WALLET", "ALIPAY"]},
                    "user_confirmed": {"type": "boolean",
                                       "description": "用户已明确确认该订单摘要后才为 true"},
                },
                "required": ["cartItemIds", "addressId", "user_confirmed"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_my_orders",
            "description": "查询当前用户订单列表。status 可选：PENDING_PAY/PENDING_SHIP/PENDING_RECEIVE/COMPLETED/AFTER_SALE/REFUNDED/CANCELED/ALL。需登录。",
            "parameters": {
                "type": "object",
                "properties": {"status": {"type": "string"}},
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "cancel_order",
            "description": "取消订单（仅待付款 PENDING_PAY 状态可取消）。需登录。",
            "parameters": {
                "type": "object",
                "properties": {"orderId": {"type": "integer"}},
                "required": ["orderId"],
            },
        },
    },
]

_MAX_RESULT_CHARS = 1800


def _trim(obj) -> str:
    """工具结果序列化并截断，控制进 LLM 的 token。"""
    s = json.dumps(obj, ensure_ascii=False) if not isinstance(obj, str) else obj
    if len(s) > _MAX_RESULT_CHARS:
        s = s[:_MAX_RESULT_CHARS] + "…(已截断)"
    return s


class ToolExecutor:
    def __init__(self, client: MelodyBackendClient | None = None):
        self.backend = client or MelodyBackendClient()

    # ---------- 供冒烟测试直接调用 ----------

    def execute(self, name: str, arguments: dict, st: sess.SessionState) -> dict:
        try:
            handler = getattr(self, f"_tool_{name}")
        except AttributeError:
            return {"error": f"未知工具 {name}"}
        try:
            return handler(arguments, st)
        except BackendError as e:
            if e.code in (1002, 401):
                return {"error": "NEED_LOGIN", "msg": e.msg}
            return {"error": e.msg, "code": e.code}
        except Exception as e:  # noqa: BLE001
            log.exception("[tool] %s failed", name)
            return {"error": f"工具执行异常：{e}"}

    # ---------- 商品（免登录）----------

    def _tool_search_products(self, args: dict, st) -> dict:
        items = self.backend.search_products(args.get("keyword"), args.get("sort"))
        top = items[:10]
        return {"total": len(items),
                "products": [{"id": p.get("id"), "name": p.get("name"), "price": p.get("price"),
                              "inventory": p.get("inventory"), "sales": p.get("sales"),
                              "rating": p.get("rating")} for p in top]}

    def _tool_get_product_detail(self, args: dict, st) -> dict:
        pid = int(args["productId"])
        p = self.backend.product_detail(pid)
        skus = self.backend.product_skus(pid)
        p["skus"] = [{"skuId": s.get("id"), "specName": s.get("specName"), "price": s.get("price"),
                      "stock": s.get("stock"), "sales": s.get("sales")} for s in skus]
        return p

    def _tool_get_categories(self, args: dict, st) -> dict:
        cats = self.backend.categories()
        return {"categories": [{"id": c.get("id"), "name": c.get("name")} for c in cats]}

    def _tool_search_knowledge(self, args: dict, st) -> dict:
        hits = get_kb().search(args.get("query", ""))
        if not hits:
            return {"answer": "知识库为空或无相关内容，请如实告知用户该问题暂无官方资料。"}
        return {"results": [{"text": h["text"], "source": h["source"], "score": h["score"]}
                            for h in hits]}

    # ---------- 个人数据（需登录）----------

    def _need_login(self, st) -> dict | None:
        if not st.token:
            return {"error": "NEED_LOGIN",
                    "msg": "用户尚未登录商城，请引导用户先登录后再操作"}
        return None

    def _tool_get_user_info(self, args: dict, st) -> dict:
        if err := self._need_login(st):
            return err
        u = self.backend.user_info(st.token)
        return {"nickname": u.get("nickname"), "username": u.get("username"),
                "rankCode": u.get("rankCode"), "growthValue": u.get("growthValue"),
                "points": u.get("points"), "balance": u.get("balance")}

    def _tool_get_my_cart(self, args: dict, st) -> dict:
        if err := self._need_login(st):
            return err
        items = self.backend.my_cart(st.token)
        return {"items": [{"cartItemId": i.get("id"), "productId": i.get("productId"),
                           "skuId": i.get("skuId"), "productName": i.get("productName"),
                           "specName": i.get("specName"), "price": i.get("price"),
                           "quantity": i.get("quantity"), "selected": i.get("selected")}
                          for i in items]}

    def _tool_add_to_cart(self, args: dict, st) -> dict:
        if err := self._need_login(st):
            return err
        item = self.backend.add_cart(st.token, int(args["productId"]),
                                     int(args["skuId"]), int(args["quantity"]))
        return {"ok": True, "cartItemId": item.get("id"),
                "msg": f"已将 {args['quantity']} 件加入购物车"}

    def _tool_get_my_addresses(self, args: dict, st) -> dict:
        if err := self._need_login(st):
            return err
        addrs = self.backend.addresses(st.token)
        return {"addresses": [{"addressId": a.get("id"), "name": a.get("name"), "phone": a.get("phone"),
                               "full": f"{a.get('province')}{a.get('city')}{a.get('district')}{a.get('detail')}",
                               "tag": a.get("tag"), "isDefault": a.get("isDefault")} for a in addrs]}

    def _tool_get_my_coupons(self, args: dict, st) -> dict:
        if err := self._need_login(st):
            return err
        status = args.get("status", "可用")
        coupons = self.backend.my_coupons(st.token, status)
        return {"status": status,
                "coupons": [{"userCouponId": c.get("id"), "couponId": c.get("couponId"),
                             "status": c.get("status"), "expireAt": str(c.get("expireAt") or "")}
                            for c in coupons]}

    # ---------- 下单（带确认护栏）----------

    def _tool_place_order(self, args: dict, st) -> dict:
        if err := self._need_login(st):
            return err
        cart_ids = [int(i) for i in (args.get("cartItemIds") or [])]
        if not cart_ids:
            return {"error": "购物车项为空"}
        address_id = int(args["addressId"])
        user_coupon_id = args.get("userCouponId")
        pay_method = args.get("payMethod", "WALLET")
        sig = sess.order_signature(cart_ids, address_id)

        if not args.get("user_confirmed"):
            # 第一阶段：服务端实时组装订单摘要 → 拒绝执行，要求 LLM 转述给用户确认
            summary = self._build_order_summary(st, cart_ids, address_id, user_coupon_id, pay_method)
            if "error" in summary:
                return summary
            st.pending_order_sig = sig
            st.pending_order_summary = summary
            return {"need_confirm": True,
                    "msg": "订单尚未获得用户确认。请把下面的订单摘要原样完整展示给用户，明确询问是否确认下单；"
                           "用户同意后携带 user_confirmed=true 重新调用，且参数保持不变。",
                    "order_summary": summary}

        # 第二阶段：声称已确认 → 校验之前确实展示过同一订单
        if st.pending_order_sig != sig or not st.pending_order_summary:
            return {"error": "请先展示订单摘要并获得用户确认（先不带 user_confirmed 调用一次）"}
        st.pending_order_sig = None
        st.pending_order_summary = None
        order = self.backend.create_order(st.token, cart_ids, address_id, user_coupon_id, pay_method)
        result = {"ok": True, "orderId": order.get("id"), "orderNo": order.get("orderNo"),
                  "totalPrice": order.get("totalPrice"), "status": order.get("status"),
                  "payMethod": order.get("payMethod")}
        if pay_method == "ALIPAY":
            result["tip"] = "支付宝沙箱订单已创建（待付款），请提醒用户到订单详情页完成付款"
        return result

    def _build_order_summary(self, st, cart_ids: list[int], address_id: int,
                             user_coupon_id, pay_method: str) -> dict:
        cart = self.backend.my_cart(st.token)
        chosen = [c for c in cart if c.get("id") in cart_ids]
        if not chosen:
            return {"error": "购物车中找不到对应商品，请重新调用 get_my_cart 确认 cartItemIds"}
        lines = [{"name": c.get("productName"), "spec": c.get("specName"),
                  "price": c.get("price"), "quantity": c.get("quantity"),
                  "subtotal": (c.get("price") or 0) * (c.get("quantity") or 0)} for c in chosen]
        goods_amount = sum(l["subtotal"] for l in lines)
        addr = next((a for a in self.backend.addresses(st.token) if a.get("id") == address_id), None)
        if not addr:
            return {"error": "收货地址无效，请重新调用 get_my_addresses"}
        discount = None
        coupon_desc = "不使用优惠券"
        if user_coupon_id:
            coupons = self.backend.my_coupons(st.token, "ALL")
            uc = next((c for c in coupons if c.get("id") == user_coupon_id), None)
            if uc:
                coupon_desc = f"用户券 #{user_coupon_id}（具体优惠以下单结果为准）"
                discount = "以系统计算为准"
        estimated = goods_amount  # 精确优惠由后端结算，摘要只保证商品与地址准确
        return {
            "items": lines,
            "goodsAmount": round(goods_amount, 2),
            "estimatedPay": round(estimated, 2) if discount is None else "后端按券计算",
            "coupon": coupon_desc,
            "address": f"{addr.get('province')}{addr.get('city')}{addr.get('district')}{addr.get('detail')} "
                       f"（{addr.get('name')} {addr.get('phone')}）",
            "payMethod": "钱包余额支付（即时扣款）" if pay_method == "WALLET" else "支付宝沙箱支付（生成待付款订单）",
        }

    def _tool_get_my_orders(self, args: dict, st) -> dict:
        if err := self._need_login(st):
            return err
        status = args.get("status", "ALL")
        orders = self.backend.my_orders(st.token, status)
        out = []
        for o in orders[:8]:
            out.append({"orderId": o.get("id"), "orderNo": o.get("orderNo"),
                        "status": o.get("status"), "totalPrice": o.get("totalPrice"),
                        "createdAt": str(o.get("createdAt") or ""),
                        "items": [{"name": i.get("productName"), "spec": i.get("specName"),
                                   "price": i.get("price"), "quantity": i.get("quantity")}
                                  for i in (o.get("items") or [])][:5]})
        return {"count": len(orders), "orders": out}

    def _tool_cancel_order(self, args: dict, st) -> dict:
        if err := self._need_login(st):
            return err
        self.backend.cancel_order(st.token, int(args["orderId"]))
        return {"ok": True, "msg": f"订单 {args['orderId']} 已取消，库存已回滚"}
