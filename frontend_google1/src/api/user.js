import http, { setAuthToken } from "./http";

// ========== 认证 ==========

// F-U-003 账号密码登录（带图片验证码）
export const loginApi = async (phone, password, captchaId, captchaCode) => {
  if (!phone || !password) throw new Error("手机号和密码不能为空");
  if (!captchaId || !captchaCode) throw new Error("请填写图片验证码");
  const data = await http.post("/auth/login", { phone, password, captchaId, captchaCode });
  if (data?.token) {
    setAuthToken(data.token);
    localStorage.setItem("access_token", data.token);
  }
  return { data };
};

// F-U-001 注册（图片验证码: captchaId + captchaCode）
export const registerApi = async (phone, password, captchaId, captchaCode) => {
  if (!phone || !password) throw new Error("手机号和密码不能为空");
  if (!captchaId || !captchaCode) throw new Error("请填写图片验证码");
  const data = await http.post("/auth/register", { phone, password, captchaId, captchaCode });
  if (data?.token) {
    setAuthToken(data.token);
    localStorage.setItem("access_token", data.token);
  }
  return { data };
};

// 获取图片验证码(4 位) → { id, code }
// 后端同时返回 code 是毕设简化,前端用 code 画 SVG
export const fetchCaptchaApi = async () => {
  return await http.get("/captcha");
};

// 退出登录
export const logoutApi = async () => {
  try {
    await http.post("/auth/logout", {});
  } catch (e) {
    // 即使接口失败也要清前端
  }
  setAuthToken(null);
  localStorage.removeItem("access_token");
};

// ========== 用户资料 ==========

// 获取当前用户资料
export const fetchUserProfile = async () => {
  return await http.get("/user");
};

// 修改资料
export const updateUserProfileApi = async (profile) => {
  return await http.put("/user", profile);
};

// ========== 收货地址 ==========

export const fetchAddresses = async () => {
  return await http.get("/address");
};

export const addAddressApi = async (address) => {
  return await http.post("/address", address);
};

export const updateAddressApi = async (address) => {
  return await http.put(`/address/${address.id}`, address);
};

export const deleteAddressApi = async (id) => {
  return await http.delete(`/address/${id}`);
};

// ========== 优惠券 ==========

// F-U-054：我的券（已领取）
export const fetchCoupons = async () => {
  return await http.get("/coupon/mine");
};

// F-U-060：可领取（首页 hero 区用）
export const fetchAvailableCoupons = async () => {
  return await http.get("/coupon/available");
};

// 全部状态（含已用/过期）的券：首页判断"是否已领过"（终身一次，不能再领）
export const fetchAllCouponsApi = async () => {
  return await http.get("/coupon/mine", { params: { status: "ALL" } });
};

export const claimCouponApi = async (couponId) => {
  return await http.post(`/coupon/${couponId}/claim`);
};

// ========== 钱包支付 ==========

// 查余额
export const fetchWalletBalance = async () => {
  return await http.get("/wallet");
};

// F-U-045：钱包支付订单（调支付接口）
export const createPaymentApi = async (orderId) => {
  return await http.post("/payment/create", { orderId });
};

// 钱包流水（F-U-046）
export const fetchWalletLogs = async () => {
  return await http.get("/wallet/logs");
};

// ========== 钱包充值（支付宝沙箱） ==========

// 创建充值单：返回 { outTradeNo, payForm }，前端新窗口打开 payForm 付款
export const createRechargeApi = async (amount) => {
  return await http.post("/wallet/recharge", null, { params: { amount } });
};

// 轮询充值结果：PAID 时返回最新余额，由支付宝异步通知/主动查询入账
export const queryRechargeApi = async (outTradeNo) => {
  return await http.get("/wallet/recharge/query", { params: { outTradeNo } });
};

// ========== 订单支付（余额 / 支付宝沙箱） ==========

// PENDING_PAY 订单余额补付
export const walletPayOrderApi = async (orderId) => {
  return await http.post(`/order/${orderId}/wallet-pay`);
};

// PENDING_PAY 订单生成支付宝支付页表单：返回 { orderNo, payForm }
export const orderAlipayPayApi = async (orderId) => {
  return await http.post(`/order/${orderId}/alipay-pay`);
};

// 主动查询订单支付状态：status = PENDING / PAID；PAID 时带 paidAt
export const orderAlipayQueryApi = async (orderId) => {
  return await http.get(`/order/${orderId}/alipay-query`);
};

// ========== 收藏 ==========

export const fetchMyFavoritesApi = async () => {
  return await http.get("/favorite");
};

export const addFavoriteApi = async (productId) => {
  return await http.post(`/favorite/${productId}`);
};

export const removeFavoriteApi = async (productId) => {
  return await http.delete(`/favorite/${productId}`);
};
