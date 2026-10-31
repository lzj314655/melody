"""商城后端 HTTP 客户端：封装 R 包装解析，统一抛 BackendError(code, msg)。"""
import logging

import httpx

from .config import settings

log = logging.getLogger(__name__)


class BackendError(Exception):
    def __init__(self, code: int, msg: str):
        self.code = code
        self.msg = msg
        super().__init__(f"[{code}] {msg}")


class MelodyBackendClient:
    """同步 httpx 客户端；Agent 在线程池中调用，避免阻塞事件循环。"""

    def __init__(self, base_url: str | None = None):
        self.base_url = (base_url or settings.backend_url).rstrip("/")
        self._client = httpx.Client(timeout=settings.http_timeout)

    # ---------- 基础 ----------

    def _request(self, method: str, path: str, *, token: str | None = None,
                 params: dict | None = None, json_body: dict | None = None,
                 extra_headers: dict | None = None):
        headers = dict(extra_headers or {})
        if token:
            headers["Authorization"] = f"Bearer {token}"
        try:
            resp = self._client.request(
                method, f"{self.base_url}{path}",
                headers=headers, params=params, json=json_body,
            )
        except httpx.HTTPError as e:
            raise BackendError(-1, f"商城后端连接失败：{e}") from e
        try:
            body = resp.json()
        except ValueError:
            raise BackendError(resp.status_code, f"后端返回非 JSON（HTTP {resp.status_code}）")
        if body.get("code") != 200:
            raise BackendError(body.get("code") or resp.status_code,
                               body.get("msg") or "请求失败")
        return body.get("data")

    # ---------- 认证（供冒烟测试脚本使用）----------

    def get_captcha(self) -> dict:
        """毕设简化：验证码接口明文返回 code，程序可直接读出。"""
        return self._request("GET", "/api/captcha")

    def login(self, phone: str, password: str) -> dict:
        cap = self.get_captcha()
        return self._request("POST", "/api/auth/login", json_body={
            "phone": phone,
            "password": password,
            "captchaId": cap["id"],
            "captchaCode": cap["code"],
        })

    # ---------- 商品（无需登录）----------

    def search_products(self, keyword: str | None = None, sort: str | None = None) -> list:
        params = {k: v for k, v in {"keyword": keyword, "sort": sort}.items() if v}
        return self._request("GET", "/api/product/list", params=params) or []

    def product_detail(self, product_id: int) -> dict:
        return self._request("GET", f"/api/product/detail/{product_id}")

    def product_skus(self, product_id: int) -> list:
        return self._request("GET", "/api/sku", params={"productId": product_id}) or []

    def categories(self) -> list:
        return self._request("GET", "/api/product/categories") or []

    # ---------- 用户 / 地址（需登录）----------

    def user_info(self, token: str) -> dict:
        return self._request("GET", "/api/user", token=token)

    def addresses(self, token: str) -> list:
        return self._request("GET", "/api/address", token=token) or []

    # ---------- 购物车（需登录）----------

    def my_cart(self, token: str) -> list:
        return self._request("GET", "/api/cart", token=token) or []

    def add_cart(self, token: str, product_id: int, sku_id: int, quantity: int) -> dict:
        return self._request("POST", "/api/cart", token=token, json_body={
            "productId": product_id, "skuId": sku_id, "quantity": quantity,
        })

    def remove_cart(self, token: str, product_id: int, sku_id: int):
        return self._request("DELETE", f"/api/cart/{product_id}/{sku_id}", token=token)

    # ---------- 优惠券（需登录）----------

    def my_coupons(self, token: str, status: str = "可用") -> list:
        return self._request("GET", "/api/coupon/mine", token=token,
                             params={"status": status}) or []

    # ---------- 订单（需登录）----------

    def create_order(self, token: str, cart_item_ids: list[int], address_id: int,
                     user_coupon_id: int | None = None, pay_method: str = "WALLET") -> dict:
        return self._request("POST", "/api/order", token=token, json_body={
            "cartItemIds": cart_item_ids,
            "addressId": address_id,
            "userCouponId": user_coupon_id,
            "payMethod": pay_method,
        })

    def my_orders(self, token: str, status: str = "ALL") -> list:
        return self._request("GET", "/api/order", token=token,
                             params={"status": status}) or []

    def order_detail(self, token: str, order_id: int) -> dict:
        return self._request("GET", f"/api/order/{order_id}", token=token)

    def order_detail_by_no(self, token: str, order_no: str) -> dict:
        return self._request("GET", f"/api/order/by-no/{order_no}", token=token)

    def cancel_order(self, token: str, order_id: int):
        return self._request("PUT", f"/api/order/{order_id}/cancel", token=token)

    # ---------- 管理员身份校验（售后后台接口用） ----------

    def admin_me(self, token: str) -> dict:
        return self._request("GET", "/api/admin/auth/me", token=token)

    # ---------- 售后工单内部接口（Python LangGraph → Java） ----------

    def _after_sale_headers(self) -> dict:
        if not settings.after_sale_internal_token:
            raise BackendError(500, "未配置 AFTER_SALE_INTERNAL_TOKEN，售后内部接口不可用")
        return {"X-After-Sale-Token": settings.after_sale_internal_token}

    def after_sale_create_ticket(self, payload: dict) -> dict:
        return self._request("POST", "/api/internal/after-sales/tickets",
                             json_body=payload, extra_headers=self._after_sale_headers())

    def after_sale_get_ticket(self, ticket_id: int) -> dict:
        return self._request("GET", f"/api/internal/after-sales/tickets/{ticket_id}",
                             extra_headers=self._after_sale_headers())

    def after_sale_update_ticket(self, ticket_id: int, payload: dict) -> dict:
        return self._request("PATCH", f"/api/internal/after-sales/tickets/{ticket_id}",
                             json_body=payload, extra_headers=self._after_sale_headers())

    def after_sale_event(self, ticket_id: int, step: str, actor: str, payload: str) -> None:
        self._request("POST", f"/api/internal/after-sales/tickets/{ticket_id}/events",
                      json_body={"step": step, "actor": actor, "payload": payload},
                      extra_headers=self._after_sale_headers())

    def after_sale_risk(self, user_id: int, product_id: int | None = None) -> dict:
        params = {"userId": user_id}
        if product_id is not None:
            params["productId"] = product_id
        return self._request("GET", "/api/internal/after-sales/risk",
                             params=params, extra_headers=self._after_sale_headers())

    def after_sale_refund(self, ticket_id: int) -> dict:
        return self._request("POST", f"/api/internal/after-sales/tickets/{ticket_id}/refund",
                             extra_headers=self._after_sale_headers())
