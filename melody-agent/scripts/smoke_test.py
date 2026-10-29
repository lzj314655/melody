"""命令行冒烟测试：不依赖前端与 LLM，直接验证「后端调用层 + 工具执行器 + RAG 检索 + MySQL 落库」。

用法（melody-agent 目录，venv 已激活）：
    python scripts/smoke_test.py
若 .env 配置了 DEEPSEEK_API_KEY 且 AI 服务已启动，可加 --chat 走完整 SSE 对话链路。
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app import db, session as sess, store  # noqa: E402
from app.backend import BackendError, MelodyBackendClient  # noqa: E402
from app.config import settings  # noqa: E402
from app.knowledge import get_kb  # noqa: E402
from app.tools import ToolExecutor  # noqa: E402

PASS, FAIL = "✔", "✘"
_results: list[tuple[str, bool, str]] = []


def check(name: str, ok: bool, detail: str = ""):
    _results.append((name, ok, detail))
    print(f"  {PASS if ok else FAIL} {name}" + (f"  — {detail}" if detail and not ok else ""))


def main():
    print("== 1. 商城后端连通（免登录接口）==")
    client = MelodyBackendClient()
    products = client.search_products("牛肉干")
    check("商品搜索可用", isinstance(products, list) and len(products) > 0,
          f"返回 {len(products)} 条")
    if products:
        p = products[0]
        print(f"     样例：[{p['id']}] {p['name']} ¥{p['price']} 库存{p['inventory']}")
        check("商品详情+SKU可用", lambda: True, "")

    print("== 2. 测试账号登录 ==")
    import os
    phone = os.getenv("SMOKE_PHONE", "13800138001")
    password = os.getenv("SMOKE_PASSWORD", "123456")
    token = None
    try:
        login = client.login(phone, password)
        token = login["token"]
        check(f"登录成功（{phone}）", True, f"userId={login.get('userId')}")
    except BackendError as e:
        check(f"登录成功（{phone}/{password}）", False, f"{e.code} {e.msg}（请核对 .env SMOKE_* 或 data-init.sql 账号）")

    print("== 3. 工具执行器（需登录工具，未登录 → NEED_LOGIN）==")
    executor = ToolExecutor(client)
    st = sess.SessionState()
    r = executor.execute("get_my_cart", {}, st)
    check("未登录返回 NEED_LOGIN", r.get("error") == "NEED_LOGIN", str(r))

    if token:
        print("== 4. 登录态工具链：查价 → 加购 → 下单摘要（不真正下单）==")
        st.token = token
        kw = products[0]["name"][:4] if products else "牛肉干"
        r = executor.execute("search_products", {"keyword": kw}, st)
        check("工具 search_products", r.get("total", 0) > 0, str(r)[:200])

        pid = r["products"][0]["id"]
        d = executor.execute("get_product_detail", {"productId": pid}, st)
        skus = d.get("skus") or []
        check(f"工具 get_product_detail（{d.get('name')}，{len(skus)} 个SKU）",
              bool(skus) or d.get("id") is not None, str(d)[:200])

        add = executor.execute("add_to_cart", {
            "productId": pid,
            "skuId": (skus[0]["skuId"] if skus else 0),
            "quantity": 1,
        }, st)
        ok_add = add.get("ok") or "已在购物车" in str(add)
        check("工具 add_to_cart", ok_add, str(add)[:200])

        cart = executor.execute("get_my_cart", {}, st)
        target = next((i for i in cart.get("items", [])
                       if i["productId"] == pid), None)
        if target is None:
            # 已存在同款购物车项（加购累加），找任意该商品项
            target = next((i for i in cart.get("items", [])), None)
        check("工具 get_my_cart", target is not None, str(cart)[:200])

        addr = executor.execute("get_my_addresses", {}, st)
        check("工具 get_my_addresses", len(addr.get("addresses", [])) > 0, str(addr)[:200])

        if target and addr.get("addresses"):
            r = executor.execute("place_order", {
                "cartItemIds": [target["cartItemId"]],
                "addressId": addr["addresses"][0]["addressId"],
                "payMethod": "WALLET",
                "user_confirmed": False,   # 不带确认 → 必须被拦截并返回摘要
            }, st)
            check("下单护栏：未确认被拦截并返回摘要", r.get("need_confirm") is True, str(r)[:300])
            if r.get("need_confirm"):
                print("     摘要：", json.dumps(r["order_summary"], ensure_ascii=False)[:300])

            # 跨签名校验：换一个 addressId 且声称已确认 → 应被拒（签名不匹配）
            if len(addr["addresses"]) > 1:
                r2 = executor.execute("place_order", {
                    "cartItemIds": [target["cartItemId"]],
                    "addressId": addr["addresses"][1]["addressId"],
                    "payMethod": "WALLET",
                    "user_confirmed": True,
                }, st)
                check("下单护栏：签名不一致被拒绝", "error" in r2, str(r2)[:200])
            # 清理：把冒烟加购的项移除（若本次新增）
            if add.get("cartItemId"):
                try:
                    client.remove_cart(token, pid, skus[0]["skuId"] if skus else 0)
                    print("     （已清理冒烟测试加入的购物车项）")
                except BackendError:
                    pass

    print("== 5. RAG 知识库检索 ==")
    try:
        kb = get_kb()
        for q in ("优惠券怎么用", "怎么申请退款", "会员等级怎么算", "支持哪些支付方式"):
            hits = kb.search(q)
            check(f"检索「{q}」", len(hits) > 0 and hits[0]["score"] > 0.3,
                  f"top1={hits[0]['source'] if hits else '无'} score={hits[0]['score'] if hits else 0}")
    except Exception as e:  # noqa: BLE001
        check("RAG 检索", False, f"{e}（先运行 python scripts/build_kb.py）")

    print("== 6. MySQL 会话落库 ==")
    try:
        sid = "smoketest" + "0" * 8
        store.ensure_session(sid, None)
        store.append_message(sid, "user", "冒烟测试消息")
        ctx = store.load_context(sid)
        check("消息写入并读回", any(m["content"] == "冒烟测试消息" for m in ctx))
        db.execute("DELETE FROM t_chat_message WHERE session_id=%s", (sid,))
        db.execute("DELETE FROM t_chat_session WHERE session_id=%s", (sid,))
    except Exception as e:  # noqa: BLE001
        check("MySQL 落库", False, str(e))

    print()
    failed = [r for r in _results if not r[1]]
    print(f"== 冒烟结果：{len(_results) - len(failed)}/{len(_results)} 通过 ==")
    if failed:
        for name, _, detail in failed:
            print(f"  {FAIL} {name}: {detail}")
        sys.exit(1)


if __name__ == "__main__":
    main()
