import http from "./http";

// ---- 图片库（后台商品配图路径选择）----
// 列出后端本地图片库 (app.image-root, 即 D:\bishe\image) 下的所有图片，供上架/编辑商品选配图
export const fetchImageListApi = async () => {
  return await http.get("/admin/product/image/list");
};

// 前台页面也会用到几个"订单 + 营销"接口，全部走 axios。

// 下单（POST /api/order）
export const createOrderApi = async ({ cartItemIds, addressId, userCouponId, payMethod }) => {
  return await http.post("/order", { cartItemIds, addressId, userCouponId, payMethod });
};

// 我的订单列表（GET /api/order?status=ALL）
export const fetchMyOrders = async (status = "ALL") => {
  return await http.get("/order", { params: { status } });
};

// 订单详情
export const fetchOrderDetail = async (id) => {
  return await http.get(`/order/${id}`);
};

// 取消订单
export const cancelOrderApi = async (id) => {
  return await http.put(`/order/${id}/cancel`);
};

// 确认收货
export const confirmOrderApi = async (id) => {
  return await http.put(`/order/${id}/confirm`);
};

// F-U-044 申请退款
export const refundApi = async (orderId, reason) => {
  return await http.post(`/order/${orderId}/refund`, { reason });
};

// 查钱包余额（统一在 api/user.js 里 export，此处不再保留重复定义）
export { fetchWalletBalance } from "@/api/user";

// ========== 后台管理 API（已接通真实后端） ==========

// ---- 订单 ----
export const fetchOrders = async (status = "ALL") => {
  return await http.get("/admin/order/list", { params: { status } });
};

// 后台订单详情（F-A-031）
export const fetchAdminOrderDetail = async (id) => {
  return await http.get(`/admin/order/${id}`);
};

export const shipOrderApi = async (orderId, trackingNo) => {
  return await http.put(`/admin/order/${orderId}/ship`, null, { params: { trackingNo } });
};

export const refundApproveApi = async (orderId) => {
  return await http.put(`/admin/order/${orderId}/refund-approve`);
};

// 后台更新订单状态（兼容旧 store 调用）
export const updateOrderStatusApi = async (orderId, status, trackingNo) => {
  if (status === "PENDING_RECEIVE" && trackingNo) {
    return await shipOrderApi(orderId, trackingNo);
  }
  if (status === "AFTER_SALE") {
    return await refundApproveApi(orderId);
  }
  throw new Error(`不支持的状态变更：${status}`);
};

// ---- 商品（走 product.js） ----
// addProductApi / updateProductApi / deleteProductApi 已在 api/product.js 导出

// 商品 SKU 列表（后台编辑弹窗回显规格价格用）
export const fetchProductSkusApi = async (productId) => {
  return await http.get("/admin/sku", { params: { productId } });
};

// ---- 优惠券 ----
export const fetchCampaigns = async () => {
  // 营销列表 = 优惠券列表
  return await http.get("/admin/coupon/list");
};

export const addCampaignApi = async (coupon) => {
  return await http.post("/admin/coupon/create", coupon);
};

export const deleteCampaignApi = async (id) => {
  return await http.delete(`/admin/coupon/${id}`);
};
export { deleteCampaignApi as deleteCouponApi };

export const updateCampaignApi = async (id, patch) => {
  return await http.put(`/admin/coupon/${id}`, patch);
};

// ---- 系统日志 ----
export const fetchSystemLogs = async () => {
  return await http.get("/admin/dashboard/logs");
};

// ---- 演示数据重置（后端为 stub，前端暴露入口供评委演示用） ----
export const resetDemoDataApi = async () => {
  return await http.post("/admin/system/data-reset");
};

// ---- 运营人员 ----
export const fetchAdmins = async () => {
  return await http.get("/admin/system/admin/list");
};

export const addAdminApi = async (admin) => {
  return await http.post("/admin/system/admin/create", admin);
};

export const deleteAdminApi = async (id) => {
  return await http.delete(`/admin/system/admin/${id}`);
};

export const resetAdminPasswordApi = async (id, newPassword) => {
  return await http.post(`/admin/system/admin/${id}/reset-password`, { newPassword });
};

// ---- 分类 ----
// 后台分类列表（admin 端，走 /admin/category/list）。
// 公开的前台分类列表见 api/product.js 的 fetchCategories。
// 这里保留 fetchCategories 这个旧名仅为兼容 store/admin.js 的现有 import。
export const fetchCategories = async () => {
  return await http.get("/admin/category/list");
};

export const addCategoryApi = async (c) => {
  return await http.post("/admin/category/create", c);
};

export const updateCategoryApi = async (id, patch) => {
  return await http.put(`/admin/category/${id}`, patch);
};

export const deleteCategoryApi = async (id) => {
  return await http.delete(`/admin/category/${id}`);
};

// ---- 用户 ----
export const fetchAdminUsers = async (keyword, rank) => {
  return await http.get("/admin/user/list", { params: { keyword, rank } });
};

export const updateUserStatusApi = async (id, status) => {
  return await http.put(`/admin/user/${id}/status`, { status });
};

export const giftPointsApi = async (id, amount) => {
  return await http.post(`/admin/user/${id}/gift-points`, { amount });
};

// ---- 看板 ----
export const fetchDashboardKpi = async () => {
  return await http.get("/admin/dashboard/kpi");
};
export const fetchGmvTrend = async () => {
  return await http.get("/admin/dashboard/gmv-trend");
};
export const fetchOrderTrend = async () => {
  return await http.get("/admin/dashboard/order-trend");
};
export const fetchTopProducts = async () => {
  return await http.get("/admin/dashboard/top-products");
};
export const fetchCategoryShare = async () => {
  return await http.get("/admin/dashboard/category-share");
};