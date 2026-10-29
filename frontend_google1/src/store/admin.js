import { defineStore } from "pinia";
import {
  fetchOrders, fetchCampaigns, fetchSystemLogs, updateOrderStatusApi,
  addCampaignApi, deleteCampaignApi, updateCampaignApi, deleteCouponApi,
  fetchCategories, addCategoryApi, updateCategoryApi, deleteCategoryApi,
  fetchAdmins, addAdminApi, deleteAdminApi, resetAdminPasswordApi,
  shipOrderApi, refundApproveApi,
  fetchDashboardKpi, fetchGmvTrend, fetchOrderTrend, fetchTopProducts, fetchCategoryShare,
  fetchAdminUsers, updateUserStatusApi, giftPointsApi,
  refundApi
} from "@/api/admin";
import { fetchProducts, addProductApi, deleteProductApi, updateProductApi } from "@/api/product";

function pad(n) { return String(n).padStart(2, "0"); }
function formatTime(v) {
  if (!v) return "";
  // 兼容 "2026-07-18T17:12:34" / "...+08:00" / "2026-07-18 17:12:34"
  const s = String(v).replace("T", " ").replace(/\..*$/, "").replace(/[+-]\d{2}:?\d{2}$/, "").trim();
  // 直接取前 19 字符（YYYY-MM-DD HH:MM:SS）
  return s.length >= 19 ? s.substring(0, 19) : s;
}

export const useAdminStore = defineStore("admin", {
  state: () => ({
    orders: [],
    campaigns: [],
    products: [],
    logs: [],
    categories: [],
    admins: [],
    users: [],
    kpi: null,
    gmvTrend: [],
    orderTrend: [],
    topProducts: [],
    categoryShare: [],
    loading: false,
    error: null,
  }),
  getters: {
    totalSales: (state) => {
      return state.orders
        .filter(o => o.status !== "PENDING_PAY" && o.status !== "CANCELED")
        .reduce((sum, o) => sum + Number(o.totalPrice || 0), 0);
    },
    ordersCount: (state) => state.orders.length,
    pendingOrdersCount: (state) => state.orders.filter(o => o.status === "PENDING_SHIP").length,
  },
  actions: {
    async loadAdminData() {
      this.loading = true;
      try {
        const [orders, campaigns, products, logs, cats] = await Promise.allSettled([
          fetchOrders(),
          fetchCampaigns(),
          fetchProducts(),
          fetchSystemLogs(),
          fetchCategories(),
        ]);
        this.orders = orders.status === "fulfilled" ? (orders.value || []) : [];
        this.campaigns = campaigns.status === "fulfilled" ? (campaigns.value || []) : [];
        this.categories = cats.status === "fulfilled" ? (cats.value || []) : [];
        // 把 products 的 categoryId 映射成 name，方便筛选
        const catMap = {};
        (this.categories || []).forEach(c => { catMap[c.id] = c.name; });
        this.products = products.status === "fulfilled"
          ? (products.value || []).map(p => ({ ...p, category: catMap[p.categoryId] || "其他" }))
          : [];
        this.logs = logs.status === "fulfilled"
          ? (logs.value || []).map(l => ({
              ...l,
              time: l.createdAt ? formatTime(l.createdAt) : (l.time || ''),
            }))
          : [];
      } catch (err) {
        this.error = err.message;
      } finally {
        this.loading = false;
      }
    },
    async loadDashboard() {
      try {
        const [kpi, gmv, ord, top, cat] = await Promise.allSettled([
          fetchDashboardKpi(),
          fetchGmvTrend(),
          fetchOrderTrend(),
          fetchTopProducts(),
          fetchCategoryShare(),
        ]);
        if (kpi.status === "fulfilled") this.kpi = kpi.value;
        if (gmv.status === "fulfilled") this.gmvTrend = gmv.value || [];
        if (ord.status === "fulfilled") this.orderTrend = ord.value || [];
        if (top.status === "fulfilled") this.topProducts = top.value || [];
        if (cat.status === "fulfilled") this.categoryShare = cat.value || [];
      } catch (err) {
        this.error = err.message;
      }
    },
    async shipOrder(orderId, trackingNo) {
      await shipOrderApi(orderId, trackingNo);
      this.orders = await fetchOrders();
    },
    async approveRefund(orderId) {
      await refundApproveApi(orderId);
      this.orders = await fetchOrders();
    },
    async updateStatus(orderId, status) {
      await updateOrderStatusApi(orderId, status);
      this.orders = await fetchOrders();
    },
    async addProduct(product) {
      await addProductApi(product);
      this.products = await fetchProducts();
    },
    async updateProduct(id, patch) {
      await updateProductApi(id, patch);
      this.products = await fetchProducts();
    },
    async deleteProduct(id) {
      await deleteProductApi(id);
      this.products = await fetchProducts();
    },
    async addCampaign(campaign) {
      await addCampaignApi(campaign);
      this.campaigns = await fetchCampaigns();
    },
    async updateCampaign(id, patch) {
      await updateCampaignApi(id, patch);
      this.campaigns = await fetchCampaigns();
    },
    async deleteCampaign(id) {
      await deleteCampaignApi(id);
      this.campaigns = await fetchCampaigns();
    },
    async deleteCoupon(id) {
      await deleteCouponApi(id);
      this.campaigns = await fetchCampaigns();
    },
    async loadCategories() {
      this.categories = await fetchCategories();
    },
    async addCategory(category) {
      await addCategoryApi(category);
      await this.loadCategories();
    },
    async updateCategory(id, patch) {
      await updateCategoryApi(id, patch);
      await this.loadCategories();
    },
    async deleteCategory(id) {
      await deleteCategoryApi(id);
      await this.loadCategories();
    },
    async loadAdmins() {
      this.admins = await fetchAdmins();
    },
    async addAdmin(admin) {
      await addAdminApi(admin);
      await this.loadAdmins();
    },
    async deleteAdmin(id) {
      await deleteAdminApi(id);
      await this.loadAdmins();
    },
    async resetAdminPassword(id, newPassword) {
      await resetAdminPasswordApi(id, newPassword);
      await this.loadAdmins();
    },
    async loadUsers(keyword = "", rank = "") {
      this.users = await fetchAdminUsers(keyword, rank);
    },
    async updateUserStatus(id, status) {
      await updateUserStatusApi(id, status);
    },
    async giftPoints(id, amount) {
      await giftPointsApi(id, amount);
    },
    // 兼容旧调用
    async refundOrder(orderId, reason) {
      await refundApi(orderId, reason);
      this.orders = await fetchOrders();
    }
  }
});