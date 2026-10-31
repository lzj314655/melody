import { createRouter, createWebHistory } from "vue-router";

// Import Frontend Views
import ProductListView from "@/views/frontend/ProductListView.vue";
import ProductDetailView from "@/views/frontend/ProductDetailView.vue";
import CartView from "@/views/frontend/CartView.vue";
import ProfileView from "@/views/frontend/ProfileView.vue";
import OrderView from "@/views/frontend/OrderView.vue";
import OrderDetailView from "@/views/frontend/OrderDetailView.vue";
import FavoritesView from "@/views/frontend/FavoritesView.vue";
import FrontendCouponsView from "@/views/frontend/CouponsView.vue";
import AddressView from "@/views/frontend/AddressView.vue";
import FrontendSettingsView from "@/views/frontend/SettingsView.vue";
import WalletLogsView from "@/views/frontend/WalletLogsView.vue";
import RechargeResultView from "@/views/frontend/RechargeResultView.vue";
import LoginView from "@/views/frontend/LoginView.vue";
import RegisterView from "@/views/frontend/RegisterView.vue";
import AfterSalesView from "@/views/frontend/AfterSalesView.vue";

// Import Admin Views
import DashboardView from "@/views/admin/DashboardView.vue";
import ProductsView from "@/views/admin/ProductsView.vue";
import OrdersView from "@/views/admin/OrdersView.vue";
import AdminOrderDetailView from "@/views/admin/AdminOrderDetailView.vue";
import AdminCouponsView from "@/views/admin/CouponsView.vue";
import UsersView from "@/views/admin/UsersView.vue";
import AdminSettingsView from "@/views/admin/SettingsView.vue";
import AdminLoginView from "@/views/admin/AdminLoginView.vue";
import CategoryView from "@/views/admin/CategoryView.vue";
import AdminManageView from "@/views/admin/AdminManageView.vue";
import AdminAfterSalesView from "@/views/admin/AfterSalesView.vue";

const routes = [
  // Frontend routes
  { path: "/", name: "home", component: ProductListView, meta: { title: "商城首页" } },
  { path: "/product/:id", name: "product-detail", component: ProductDetailView, meta: { title: "商品详情" } },
  { path: "/cart", name: "cart", component: CartView, meta: { title: "购物车", requiresAuth: true } },
  { path: "/profile", name: "profile", component: ProfileView, meta: { title: "个人中心", requiresAuth: true } },
  { path: "/order", name: "order", component: OrderView, meta: { title: "我的订单", requiresAuth: true } },
  { path: "/order/:id", name: "order-detail", component: OrderDetailView, meta: { title: "订单详情", requiresAuth: true } },
  { path: "/after-sales/:orderId", name: "after-sales", component: AfterSalesView, meta: { title: "智能客服", requiresAuth: true } },
  { path: "/favorites", name: "favorites", component: FavoritesView, meta: { title: "我的收藏", requiresAuth: true } },
  { path: "/coupons", name: "coupons", component: FrontendCouponsView, meta: { title: "领券中心", requiresAuth: true } },
  { path: "/address", name: "address", component: AddressView, meta: { title: "收货地址", requiresAuth: true } },
  { path: "/settings", name: "settings", component: FrontendSettingsView, meta: { title: "账号设置", requiresAuth: true } },
  { path: "/wallet-logs", name: "wallet-logs", component: WalletLogsView, meta: { title: "钱包流水", requiresAuth: true } },
  { path: "/recharge-result", name: "recharge-result", component: RechargeResultView, meta: { title: "充值结果", requiresAuth: true } },
  { path: "/login", name: "login", component: LoginView, meta: { title: "会员登录" } },
  { path: "/register", name: "register", component: RegisterView, meta: { title: "会员注册" } },

  // Admin routes
  { path: "/admin", name: "admin-dashboard", component: DashboardView, meta: { title: "管理后台 - 数据大屏", requiresAdmin: true } },
  { path: "/admin/products", name: "admin-products", component: ProductsView, meta: { title: "管理后台 - 商品管理", requiresAdmin: true } },
  { path: "/admin/categories", name: "admin-categories", component: CategoryView, meta: { title: "管理后台 - 分类管理", requiresAdmin: true } },
  { path: "/admin/orders", name: "admin-orders", component: OrdersView, meta: { title: "管理后台 - 订单管理", requiresAdmin: true } },
  { path: "/admin/after-sales", name: "admin-after-sales", component: AdminAfterSalesView, meta: { title: "管理后台 - 售后工作台", requiresAdmin: true } },
  { path: "/admin/orders/:id", name: "admin-order-detail", component: AdminOrderDetailView, meta: { title: "管理后台 - 订单详情", requiresAdmin: true } },
  { path: "/admin/coupons", name: "admin-coupons", component: AdminCouponsView, meta: { title: "管理后台 - 营销管理", requiresAdmin: true } },
  { path: "/admin/users", name: "admin-users", component: UsersView, meta: { title: "管理后台 - 用户管理", requiresAdmin: true } },
  { path: "/admin/settings", name: "admin-settings", component: AdminSettingsView, meta: { title: "管理后台 - 系统设置", requiresAdmin: true } },
  { path: "/admin/manage", name: "admin-manage", component: AdminManageView, meta: { title: "管理后台 - 管理员账号", requiresAdmin: true } },
  { path: "/admin/login", name: "admin-login", component: AdminLoginView, meta: { title: "管理员安全登录" } },

  { path: "/:pathMatch(.*)*", redirect: "/" }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() { return { top: 0 }; }
});

router.beforeEach((to, from, next) => {
  const defaultTitle = "麦乐迪零食商场";
  document.title = to.meta.title ? `${to.meta.title} - ${defaultTitle}` : defaultTitle;

  const userToken = localStorage.getItem("access_token");
  const adminToken = localStorage.getItem("admin_token");

  if (to.meta.requiresAdmin) {
    if (!adminToken) {
      return next({ path: "/admin/login", query: { redirect: to.fullPath } });
    }
    // 后端会校验角色；前端这里只校验 token 是否存在
    return next();
  }

  if (to.meta.requiresAuth && !userToken) {
    return next({ path: "/login", query: { redirect: to.fullPath } });
  }

  // 已登录访问 /login /register 时，跳回首页（避免重复登录）
  if (userToken && (to.path === "/login" || to.path === "/register")) {
    return next("/");
  }
  // 已管理员登录访问 /admin/login 时，跳到 dashboard
  if (adminToken && to.path === "/admin/login") {
    return next("/admin");
  }

  next();
});

export default router;
