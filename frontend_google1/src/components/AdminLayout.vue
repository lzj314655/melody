<script setup>
import { useRouter, useRoute } from "vue-router";
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import {
  LayoutDashboard,
  Package,
  ShoppingBag,
  Megaphone,
  Users,
  Settings,
  LogOut,
  Search,
  Bell,
  ChevronRight,
  FolderTree,
  UserCog,
  ShoppingCart,
  UserPlus,
  AlertTriangle,
  ShieldAlert,
  Tag
} from "lucide-vue-next";
import { setAdminToken } from "@/api/http";
import { fetchOrders, fetchCampaigns, fetchDashboardKpi } from "@/api/admin";
import { fetchProducts } from "@/api/product";
import { useAdminTheme } from "@/composables/useTheme";
import { Sun, Moon } from "lucide-vue-next";

const props = defineProps({
  title: {
    type: String,
    default: "管理后台"
  },
  subtitle: {
    type: String,
    default: "麦乐迪零食商场后台管理系统"
  },
  breadcrumbs: {
    type: Array,
    default: () => ["首页"]
  },
  /** 宽内容页（数据大屏用）：内容不套白卡容器，主题跟随全局切换 */
  fluid: {
    type: Boolean,
    default: false
  }
});

const { isDark: themeDark, toggle: toggleTheme } = useAdminTheme();
// 深浅外壳完全由全局主题决定（全后台共用一个切换）
const shell = () => themeDark.value;

const router = useRouter();
const route = useRoute();

const menuItems = [
  { path: "/admin", label: "数据大屏", icon: LayoutDashboard },
  { path: "/admin/products", label: "商品管理", icon: Package },
  { path: "/admin/categories", label: "分类管理", icon: FolderTree },
  { path: "/admin/orders", label: "订单管理", icon: ShoppingBag },
  { path: "/admin/after-sales", label: "售后工作台", icon: ShieldAlert },
  { path: "/admin/coupons", label: "营销管理", icon: Megaphone },
  { path: "/admin/users", label: "用户管理", icon: Users },
  { path: "/admin/settings", label: "系统设置", icon: Settings },
  { path: "/admin/manage", label: "管理员账号", icon: UserCog },
];

const navigate = (path) => {
  router.push(path);
};

const goToFrontend = () => {
  // 后台 → 前台：硬跳转并清掉 admin token，避免前台请求带上 admin bearer 导致后端 401 误判
  setAdminToken(null);
  window.location.href = "/";
};

const handleLogout = () => {
  if (!confirm("确认退出管理后台？")) return;
  setAdminToken(null);
  router.push("/admin/login");
};

// ============== 全局搜索 ==============
const searchQuery = ref("");
const searchOpen = ref(false);

const searchResults = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) return [];
  return menuItems
    .filter((m) => m.label.toLowerCase().includes(q))
    .slice(0, 6);
});

const submitSearch = () => {
  const first = searchResults.value[0];
  if (first) {
    navigate(first.path);
    searchQuery.value = "";
    searchOpen.value = false;
  }
};

const pickSearchResult = (item) => {
  navigate(item.path);
  searchQuery.value = "";
  searchOpen.value = false;
};

// ============== 通知中心 ==============
const notificationsOpen = ref(false);
const NOTIFY_READ_KEY = "melody-admin-notify-read";

const loadReadIds = () => {
  try {
    const raw = localStorage.getItem(NOTIFY_READ_KEY);
    return raw ? new Set(JSON.parse(raw)) : new Set();
  } catch {
    return new Set();
  }
};

const saveReadIds = (readSet) => {
  try {
    localStorage.setItem(NOTIFY_READ_KEY, JSON.stringify([...readSet]));
  } catch {
    /* ignore quota / private mode */
  }
};

const readIds = loadReadIds();

// ===== 系统通知：真实数据（按需组装，接口失败则该类通知不展示） =====
const notifications = ref([]);
const notifyLoading = ref(true);

const daysTo = (dateStr) => {
  if (!dateStr) return null;
  try {
    const d = new Date(String(dateStr).replace(" ", "T"));
    if (isNaN(d.getTime())) return null;
    return Math.ceil((d.getTime() - Date.now()) / 86400000);
  } catch {
    return null;
  }
};

const loadNotifications = async () => {
  notifyLoading.value = true;
  const list = [];
  // 1) 待发货订单
  try {
    const ship = await fetchOrders("PENDING_SHIP");
    if (ship?.length) {
      list.push({
        id: "ship",
        icon: ShoppingCart,
        title: `${ship.length} 笔订单待发货`,
        desc: ship[0].orderNo ? `最新：${ship[0].orderNo}` : "请尽快处理发货",
        time: "刚刚",
        path: "/admin/orders"
      });
    }
  } catch { /* skip */ }
  // 2) 售后申请
  try {
    const after = await fetchOrders("AFTER_SALE");
    if (after?.length) {
      list.push({
        id: "afterSale",
        icon: AlertTriangle,
        title: `${after.length} 笔售后申请待审核`,
        desc: "请及时处理退款/售后请求",
        time: "刚刚",
        path: "/admin/orders"
      });
    }
  } catch { /* skip */ }
  // 3) 库存预警（剩余件数 < 30）
  try {
    const products = await fetchProducts();
    const low = (products || []).filter(p => Number(p.inventory) < 30);
    if (low.length) {
      low.sort((a, b) => a.inventory - b.inventory);
      list.push({
        id: "stock",
        icon: Package,
        title: `${low.length} 个商品库存告急`,
        desc: `「${low[0].name}」仅剩 ${low[0].inventory} 件`,
        time: "刚刚",
        path: "/admin/products"
      });
    }
  } catch { /* skip */ }
  // 4) 优惠券即将到期（7 天内）
  try {
    const coupons = await fetchCampaigns();
    const expiring = (coupons || []).filter(c => {
      const d = daysTo(c.endDate);
      return d !== null && d >= 0 && d <= 7 && Number(c.remaining ?? 0) > 0;
    });
    if (expiring.length) {
      expiring.sort((a, b) => daysTo(a.endDate) - daysTo(b.endDate));
      list.push({
        id: "coupon",
        icon: Tag,
        title: `${expiring.length} 张优惠券即将到期`,
        desc: `「${expiring[0].name}」${daysTo(expiring[0].endDate) === 0 ? "今天" : daysTo(expiring[0].endDate) + " 天后"}过期`,
        time: "刚刚",
        path: "/admin/coupons"
      });
    }
  } catch { /* skip */ }
  // 5) 今日订单
  try {
    const kpi = await fetchDashboardKpi();
    const today = Number(kpi?.todayOrders ?? 0);
    if (today > 0) {
      list.push({
        id: "todayOrders",
        icon: UserPlus,
        title: `今日新增 ${today} 笔订单`,
        desc: "订单量持续增长，保持关注",
        time: "刚刚",
        path: "/admin/orders"
      });
    }
  } catch { /* skip */ }
  notifications.value = list.map((n) => ({ ...n, unread: !readIds.has(n.id) }));
  notifyLoading.value = false;
};

const unreadCount = computed(
  () => notifications.value.filter((n) => n.unread).length
);

// ===== 实时新订单推送（SSE）：前台一下单，后台立即弹通知 =====
const showNewOrderToast = ref(false);
const newOrderInfo = ref(null);
let newOrderToastTimer = null;
let notifySource = null;

const startNotifyStream = () => {
  try {
    // SSE 必须指向后端绝对地址（与 api/http.js 的 baseURL 同源规则一致），
    // 相对路径会打到 vite dev server 自身导致 404；
    // Docker 下注入 VITE_API_BASE=/api 走 nginx 同源反代
    const apiBase = import.meta.env.VITE_API_BASE || "http://localhost:8080";
    notifySource = new EventSource(`${apiBase}/api/notify/stream`.replace("/api/api", "/api"));
    notifySource.addEventListener("NEW_ORDER", (ev) => {
      let d = null;
      try { d = JSON.parse(ev.data); } catch { /* ignore */ }
      if (!d) return;
      // 右上角弹新订单提示
      newOrderInfo.value = d;
      showNewOrderToast.value = true;
      if (newOrderToastTimer) clearTimeout(newOrderToastTimer);
      newOrderToastTimer = setTimeout(() => { showNewOrderToast.value = false; }, 6000);
      // 刷新通知中心（待发货数会 +1）
      loadNotifications();
    });
  } catch { /* EventSource 不可用则降级为打开面板时刷新 */ }
};

const toggleNotifications = () => {
  notificationsOpen.value = !notificationsOpen.value;
  if (notificationsOpen.value) searchOpen.value = false;
};

const markAllRead = () => {
  notifications.value.forEach((n) => {
    if (n.unread) {
      n.unread = false;
      readIds.add(n.id);
    }
  });
  saveReadIds(readIds);
};

const openNotification = (n) => {
  if (n.unread) {
    n.unread = false;
    readIds.add(n.id);
    saveReadIds(readIds);
  }
  notificationsOpen.value = false;
  if (n.path) router.push(n.path);
};

const handleGlobalClick = (e) => {
  // 任何一个面板点外面都关闭
  const root = e.target;
  if (!root.closest?.("[data-search-panel]") && !root.closest?.("[data-search-input]")) {
    searchOpen.value = false;
  }
  if (!root.closest?.("[data-notify-panel]") && !root.closest?.("[data-notify-btn]")) {
    notificationsOpen.value = false;
  }
};

onMounted(() => {
  document.addEventListener("click", handleGlobalClick);
  loadNotifications();
  startNotifyStream();
});
onBeforeUnmount(() => {
  document.removeEventListener("click", handleGlobalClick);
  if (notifySource) notifySource.close();
  if (newOrderToastTimer) clearTimeout(newOrderToastTimer);
});
</script>

<template>
  <div
    :class="[
      'min-h-screen flex font-sans antialiased w-full',
      shell() ? 'admin-theme-dark bg-[#0f172a] text-[#e2e8f0]' : 'bg-[#fbf9f5] text-[#1b1c1a]',
    ]"
  >
    <!-- 新订单实时通知 -->
    <Transition name="notify-fade">
      <div
        v-if="showNewOrderToast && newOrderInfo"
        class="fixed top-6 right-6 z-[60] bg-white border border-[#e4e2de] rounded-2xl shadow-xl px-5 py-4 flex items-start gap-3 max-w-sm cursor-pointer"
        @click="showNewOrderToast = false"
      >
        <div class="shrink-0 w-10 h-10 rounded-xl bg-[#ff6b35] text-white flex items-center justify-center shadow-md">
          <ShoppingCart class="w-5 h-5" />
        </div>
        <div class="min-w-0">
          <p class="text-sm font-bold text-stone-800 flex items-center gap-2">
            新订单来了！
            <span class="w-1.5 h-1.5 rounded-full bg-[#ff6b35] animate-pulse"></span>
          </p>
          <p class="text-xs text-stone-500 mt-1 font-mono">{{ newOrderInfo.orderNo }}</p>
          <p class="text-xs text-stone-600 mt-0.5">
            实付
            <span class="text-[#ff6b35] font-bold">￥{{ Number(newOrderInfo.totalPrice).toFixed(2) }}</span>
            <span class="text-stone-400 ml-2">{{ newOrderInfo.payMethod === "ALIPAY" ? "待付款" : "待发货" }}</span>
          </p>
        </div>
      </div>
    </Transition>
    <!-- Sidebar -->
    <aside :class="['w-64 flex flex-col justify-between shrink-0', shell() ? 'sidebar-grad' : 'bg-white border-r border-[#e4e2de]']">
      <div>
        <!-- Brand Header -->
        <div :class="['border-b', shell() ? 'px-5 py-6 border-white/[0.07]' : 'p-6 border-[#e4e2de]']">
          <div v-if="shell()" class="flex items-center gap-3">
            <span class="w-10 h-10 rounded-xl bg-gradient-to-br from-[#22d3ee] via-[#818cf8] to-[#a78bfa] flex items-center justify-center text-white font-black text-lg shadow-lg shadow-indigo-500/40">
              M
            </span>
            <div class="min-w-0">
              <h1 class="text-[15px] font-bold tracking-tight font-display leading-tight text-white">麦乐迪管理台</h1>
              <p class="text-[10px] mt-0.5 font-mono tracking-widest uppercase text-indigo-200/40">Admin Console</p>
            </div>
          </div>
          <div v-else>
            <h1 class="text-xl font-bold tracking-tight text-[#ff6b35] font-display">麦乐迪管理台</h1>
            <p class="text-xs text-stone-500 mt-1 font-mono">系统管理员</p>
          </div>
        </div>

        <!-- Navigation Menu -->
        <nav :class="shell() ? 'px-3 pt-5 space-y-1' : 'p-4 space-y-1.5 mt-4'">
          <a
            v-for="item in menuItems"
            :key="item.path"
            @click.prevent="navigate(item.path)"
            :href="item.path"
            :class="`relative flex items-center gap-3 rounded-xl font-medium transition-all duration-200 cursor-pointer group ${
              shell() ? 'px-3.5 py-2.5 text-[13px]' : 'px-4 py-3 text-sm'
            } ${
              route.path === item.path
                ? (shell()
                    ? 'bg-[#6366f1]/16 text-indigo-200 font-semibold'
                    : 'bg-[#ffdbcc] text-[#351000] font-semibold')
                : (shell()
                    ? 'text-slate-400 hover:bg-white/[0.06] hover:text-slate-100'
                    : 'text-[#5a4136] hover:bg-[#efeeea] hover:text-[#1b1c1a]')
            }`"
          >
            <span v-if="route.path === item.path && shell()" class="side-indicator"></span>
            <component
              :is="item.icon"
              :class="`transition-colors ${
                shell() ? 'w-[17px] h-[17px]' : 'w-4.5 h-4.5'
              } ${
                route.path === item.path
                  ? (shell() ? 'text-indigo-300' : 'text-[#351000]')
                  : (shell() ? 'text-slate-500 group-hover:text-slate-300' : 'text-[#5a4136]')
              }`"
            />
            <span>{{ item.label }}</span>
          </a>
        </nav>
      </div>

      <!-- Footer Admin User profile -->
      <div :class="['p-4 border-t flex items-center justify-between', shell() ? 'border-white/[0.07] bg-white/[0.02]' : 'border-[#e4e2de] bg-[#efeeea]/40']">
        <div class="flex items-center gap-3 min-w-0">
          <img
            src="https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&q=80&w=100"
            alt="Admin Avatar"
            :class="['w-10 h-10 rounded-full object-cover', shell() ? 'border border-white/15 ring-2 ring-[#ff6b35]/30' : 'border border-[#dbdad6]']"
          />
          <div class="min-w-0">
            <h4 :class="['font-semibold truncate', shell() ? 'text-[13px] text-stone-200' : 'text-sm text-stone-800']">Melody Admin</h4>
            <p :class="['text-stone-500 truncate', shell() ? 'text-[11px]' : 'text-xs']">admin@melody.com</p>
          </div>
        </div>
        <button
          @click="handleLogout"
          title="退出登录"
          :class="['rounded-md transition-colors cursor-pointer', shell() ? 'p-2 text-stone-500 hover:bg-red-500/15 hover:text-red-400' : 'p-1.5 text-[#5a4136] hover:bg-red-50 hover:text-red-500']"
        >
          <LogOut class="w-4 h-4" />
        </button>
      </div>
    </aside>

    <!-- Main Container -->
    <div :class="['flex-1 flex flex-col min-w-0 overflow-y-auto', shell() && 'dark-screen']">
      <!-- Top bar header -->
      <header
        :class="[
          'flex items-center justify-between shrink-0 border-b transition-colors',
          shell()
            ? 'h-14 px-6 lg:px-8 bg-[#0f172a]/70 backdrop-blur-md border-white/[0.08]'
            : 'h-16 px-8 bg-white border-[#e4e2de]',
        ]"
      >
        <div class="flex items-center gap-2">
          <span v-if="shell()" class="text-[11px] font-mono tracking-[0.2em] uppercase text-indigo-200/40">Melody Console</span>
          <span v-else class="text-lg font-bold text-stone-800 font-display">管理系统</span>
        </div>

        <div class="flex items-center gap-4">
          <!-- Search -->
          <div class="relative w-64">
            <input
              data-search-input
              v-model="searchQuery"
              @focus="searchOpen = true"
              @keydown.enter="submitSearch"
              @keydown.esc="searchOpen = false"
              type="text"
              placeholder="搜索全局功能..."
              :class="[
                'w-full text-xs pl-8 pr-3 py-2 rounded-lg border focus:outline-none transition-colors',
                shell()
                  ? 'bg-white/[0.06] border-white/10 focus:border-cyan-400/60 text-slate-200 placeholder:text-slate-500'
                  : 'bg-[#f5f3ef] border-[#e4e2de] focus:border-[#ff6b35] text-stone-800',
              ]"
            />
            <Search class="w-3.5 h-3.5 text-stone-400 absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />

            <!-- Search dropdown -->
            <div
              v-if="searchOpen && searchQuery.trim()"
              data-search-panel
              class="absolute right-0 top-full mt-2 w-80 bg-white border border-[#e4e2de] rounded-xl shadow-lg overflow-hidden z-50"
            >
              <div class="px-4 py-2.5 text-xs text-stone-500 border-b border-[#efeeea] font-mono">
                全局菜单搜索
              </div>
              <ul v-if="searchResults.length" class="max-h-80 overflow-y-auto">
                <li
                  v-for="item in searchResults"
                  :key="item.path"
                  @click="pickSearchResult(item)"
                  class="flex items-center gap-3 px-4 py-2.5 hover:bg-[#f5f3ef] cursor-pointer transition-colors"
                >
                  <component :is="item.icon" class="w-4 h-4 text-[#5a4136] shrink-0" />
                  <span class="text-sm text-stone-800 truncate">{{ item.label }}</span>
                  <ChevronRight class="w-3.5 h-3.5 text-stone-400 ml-auto shrink-0" />
                </li>
              </ul>
              <div v-else class="px-4 py-6 text-center text-xs text-stone-400">
                没有匹配的功能，试试 “商品” 或 “订单”
              </div>
              <div class="px-4 py-2 border-t border-[#efeeea] text-[11px] text-stone-400 font-mono">
                按 Enter 跳转到第一条结果
              </div>
            </div>
          </div>

          <!-- Notification -->
          <div class="relative">
            <button
              data-notify-btn
              @click="toggleNotifications"
              title="系统通知"
              :class="[
                'rounded-full relative transition-colors cursor-pointer',
                shell() ? 'p-2 text-slate-400 hover:text-slate-100 hover:bg-white/[0.06]' : 'p-2 text-stone-500 hover:text-stone-800 hover:bg-[#f5f3ef]',
              ]"
            >
              <Bell class="w-5 h-5" />
              <span
                v-if="unreadCount > 0"
                class="absolute top-1 right-1 min-w-[16px] h-4 px-1 bg-[#ff6b35] text-white text-[10px] font-bold rounded-full ring-2 ring-white flex items-center justify-center"
              >
                {{ unreadCount > 9 ? "9+" : unreadCount }}
              </span>
            </button>

            <!-- Notification dropdown -->
            <div
              v-if="notificationsOpen"
              data-notify-panel
              class="absolute right-0 top-full mt-2 w-80 bg-white border border-[#e4e2de] rounded-xl shadow-lg overflow-hidden z-50"
            >
              <div class="flex items-center justify-between px-4 py-2.5 border-b border-[#efeeea]">
                <span class="text-sm font-semibold text-stone-800">系统通知</span>
                <button
                  v-if="unreadCount > 0"
                  @click="markAllRead"
                  class="text-xs text-[#ff6b35] hover:underline cursor-pointer"
                >
                  全部已读
                </button>
              </div>
              <ul class="max-h-96 overflow-y-auto">
                <li
                  v-if="notifyLoading"
                  class="px-4 py-8 text-center text-xs text-stone-400"
                >
                  正在加载通知…
                </li>
                <li
                  v-else-if="notifications.length === 0"
                  class="px-4 py-8 text-center text-xs text-stone-400"
                >
                  暂无系统通知
                </li>
                <li
                  v-for="n in notifications"
                  :key="n.id"
                  @click="openNotification(n)"
                  class="flex items-start gap-3 px-4 py-3 hover:bg-[#f5f3ef] cursor-pointer transition-colors border-b border-[#efeeea] last:border-b-0"
                >
                  <div
                    :class="`shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${
                      n.unread ? 'bg-[#ffdbcc] text-[#351000]' : 'bg-[#efeeea] text-stone-500'
                    }`"
                  >
                    <component :is="n.icon" class="w-4 h-4" />
                  </div>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2">
                      <p class="text-sm font-medium text-stone-800 truncate">{{ n.title }}</p>
                      <span
                        v-if="n.unread"
                        class="w-1.5 h-1.5 rounded-full bg-[#ff6b35] shrink-0"
                      ></span>
                    </div>
                    <p class="text-xs text-stone-500 mt-0.5 truncate">{{ n.desc }}</p>
                    <p class="text-[11px] text-stone-400 mt-1 font-mono">{{ n.time }}</p>
                  </div>
                </li>
              </ul>
              <div class="px-4 py-2 border-t border-[#efeeea] text-center">
                <button
                  @click="navigate('/admin/orders'); notificationsOpen = false"
                  class="text-xs text-stone-500 hover:text-[#ff6b35] cursor-pointer"
                >
                  查看全部
                </button>
              </div>
            </div>
          </div>

          <!-- 主题切换 -->
          <button
            @click="toggleTheme"
            :title="themeDark ? '切换到浅色模式' : '切换到深色模式'"
            :class="[
              'p-2 rounded-full transition-all cursor-pointer hover:rotate-12',
              shell()
                ? 'text-amber-300 hover:bg-white/[0.08]'
                : 'text-stone-500 hover:text-amber-500 hover:bg-amber-50',
            ]"
          >
            <Sun v-if="themeDark" class="w-5 h-5" />
            <Moon v-else class="w-5 h-5" />
          </button>

          <div :class="['h-8 w-px', shell() ? 'bg-white/10' : 'bg-[#e4e2de]']"></div>

          <button
            @click="goToFrontend"
            :class="[
              'text-xs px-3 py-1.5 rounded-lg font-medium transition-colors cursor-pointer',
              shell()
                ? 'bg-cyan-400/10 text-cyan-300 hover:bg-cyan-400/20'
                : 'bg-[#004e64]/10 text-[#004e64] hover:bg-[#004e64]/20',
            ]"
          >
            前台商城
          </button>
        </div>
      </header>

      <!-- Content Body -->
      <main class="flex-1 p-8">
        <!-- Breadcrumbs -->
        <nav
          :class="['flex items-center font-mono', shell() ? 'gap-1.5 text-[11px] mb-2.5 text-slate-500' : 'gap-1.5 text-xs mb-4 text-stone-500']"
        >
          <span
            :class="['cursor-pointer', shell() ? 'hover:text-cyan-300' : 'hover:text-[#ff6b35]']"
            @click="goToFrontend"
            >商场首页</span
          >
          <template v-for="(crumb, idx) in breadcrumbs" :key="idx">
            <ChevronRight :class="shell() ? 'w-3 h-3' : 'w-3.5 h-3.5'" />
            <span :class="idx === breadcrumbs.length - 1 ? (shell() ? 'text-slate-300 font-medium' : 'text-stone-800 font-medium') : 'cursor-pointer'">
              {{ crumb }}
            </span>
          </template>
        </nav>

        <!-- Heading with actions -->
        <div :class="['flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6', shell() ? 'gap-3' : 'gap-4']">
          <div>
            <h2
              :class="['font-bold tracking-tight font-display', shell() ? 'text-xl leading-tight text-white' : 'text-2xl text-stone-800']"
            >
              {{ title }}
            </h2>
            <p :class="['mt-1', shell() ? 'text-[12px] text-slate-500' : 'text-sm text-stone-500']">
              {{ subtitle }}
            </p>
          </div>
          <div class="flex items-center gap-3">
            <slot name="actions" />
          </div>
        </div>

        <!-- Slot Content Container：宽内容页（大屏）不套白卡，其余页面套卡片（深色时自动玻璃化） -->
        <div v-if="!fluid" class="bg-white rounded-xl border border-[#e4e2de] p-6 shadow-sm">
          <slot />
        </div>
        <div v-else>
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.notify-fade-enter-active, .notify-fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.notify-fade-enter-from, .notify-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 侧边栏：比内容区更深一阶，深色带靛蓝微光 / 浅色暖白（token 驱动） */
.sidebar-grad {
  background:
    radial-gradient(320px 240px at 110% -5%, rgba(99, 102, 241, 0.16), transparent 60%),
    linear-gradient(180deg, #0b1120 0%, #0e162b 100%);
}
.sidebar-light {
  background:
    radial-gradient(300px 200px at 110% -5%, rgba(99, 102, 241, 0.10), transparent 60%),
    linear-gradient(180deg, #ffffff 0%, #f7f8fa 100%);
  border-right: 1px solid rgba(15, 23, 42, 0.08);
}
</style>
