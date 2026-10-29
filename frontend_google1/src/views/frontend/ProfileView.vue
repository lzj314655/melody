<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import { couponFace } from "@/utils/coupon";
import { fetchWalletBalance, createRechargeApi } from "@/api/user";
import { fetchMyOrders, confirmOrderApi } from "@/api/admin";
import { fetchRecentViewsApi } from "@/api/behavior";
import FrontendLayout from "@/components/FrontendLayout.vue";
import UserAvatar from "@/components/UserAvatar.vue";
import {
  User, ShoppingBag, MapPin, Ticket, Plus, Trash2, ChevronRight, CheckCircle,
  Coins, ShieldCheck, Calendar, History
} from "lucide-vue-next";

const router = useRouter();
const userStore = useUserStore();

const activeTab = ref("orders"); // 地址管理走独立页 /address，避免在 ProfileView 重复渲染
const orders = ref([]);
const walletBalance = ref(0);

const recentViews = ref([]);
const recentViewsLoading = ref(false);

const showAddAddressModal = ref(false);
const showRechargeModal = ref(false);
const rechargeAmount = ref(100);
const rechargeSubmitting = ref(false);
const addrName = ref("");
const addrPhone = ref("");
const addrProvince = ref("上海市");
const addrCity = ref("上海市");
const addrDistrict = ref("浦东新区");
const addrDetail = ref("");
const addrTag = ref("家");
const addrIsDefault = ref(false);

async function loadAll() {
  await userStore.loadUser().catch(() => {});
  await userStore.loadAddresses().catch(() => {});
  await userStore.loadCoupons().catch(() => {});
  await userStore.loadAvailableCoupons().catch(() => {});
  try {
    const w = await fetchWalletBalance();
    walletBalance.value = Number(w?.balance ?? 0);
  } catch {
    walletBalance.value = 0;
  }
  try {
    const list = await fetchMyOrders("ALL");
    orders.value = (list || []).slice(0, 5);
  } catch {
    orders.value = [];
  }
}

onMounted(loadAll);

const loadRecentViews = async () => {
  recentViewsLoading.value = true;
  try {
    recentViews.value = (await fetchRecentViewsApi(20)) || [];
  } catch {
    recentViews.value = [];
  } finally {
    recentViewsLoading.value = false;
  }
};

// F-U-XXX 每次切到"近期浏览"tab 都重拉，保证顺序是最新的浏览时间
watch(activeTab, (tab) => {
  if (tab === "recent-views") loadRecentViews();
});

const statusMap = {
  PENDING_PAY: "待付款",
  PENDING_SHIP: "待发货",
  PENDING_RECEIVE: "待收货",
  COMPLETED: "已完成",
  CANCELED: "已取消",
  AFTER_SALE: "售后中",
  REFUNDED: "已退款",
};

const openRechargeModal = () => {
  rechargeAmount.value = 100;
  showRechargeModal.value = true;
};
const closeRechargeModal = () => {
  showRechargeModal.value = false;
};
const handleRecharge = async () => {
  const amount = Number(rechargeAmount.value);
  if (!Number.isFinite(amount) || amount <= 0) {
    alert("请填写正确的充值金额（大于 0）");
    return;
  }
  if (amount > 100000) {
    alert("单次充值金额不得超过 ￥100,000");
    return;
  }
  rechargeSubmitting.value = true;
  try {
    // 演示环境：已去掉支付宝沙箱支付，充值直接到账
    await createRechargeApi(amount);
    closeRechargeModal();
    // 刷新余额显示
    try {
      const w = await fetchWalletBalance();
      walletBalance.value = Number(w?.balance ?? 0);
    } catch {}
    alert(`充值成功！已到账 ￥${amount.toFixed(2)}`);
  } catch (e) {
    alert("充值失败：" + e.message);
  } finally {
    rechargeSubmitting.value = false;
  }
};

const handleLogout = async () => {
  await userStore.logout();
  router.push("/login");
};

const handleAddAddress = async () => {
  if (!addrName.value || !addrPhone.value || !addrDetail.value) {
    alert("请填齐收货人姓名、手机和详细地址哦");
    return;
  }
  const payload = {
    consignee: addrName.value,
    name: addrName.value,
    phone: addrPhone.value,
    province: addrProvince.value,
    city: addrCity.value,
    district: addrDistrict.value,
    detail: addrDetail.value,
    address: `${addrProvince.value} ${addrCity.value} ${addrDistrict.value} ${addrDetail.value}`,
    tag: addrTag.value,
    isDefault: addrIsDefault.value ? 1 : 0,
  };
  try {
    await userStore.addAddress(payload);
    showAddAddressModal.value = false;
    addrName.value = "";
    addrPhone.value = "";
    addrDetail.value = "";
    addrIsDefault.value = false;
    alert("收货地址添加成功！");
  } catch (err) {
    alert(err.message);
  }
};

// F-U-051 把某条地址设为默认（避免模板里直接调 action 漏掉 await/alert）
const handleSetDefault = async (addr) => {
  try {
    await userStore.updateAddress({ ...addr, isDefault: 1 });
    alert("已成功设为默认地址");
  } catch (err) {
    alert("设为默认失败: " + (err?.message || "未知错误"));
  }
};

const handleConfirmDelivery = async (orderId) => {
  if (!confirm("确认已收到商品吗？")) return;
  try {
    await confirmOrderApi(orderId);
    await loadAll();
    alert("订单已确认收货！");
  } catch (e) {
    alert(e.message);
  }
};

const formatDate = (s) => {
  if (!s) return "";
  try {
    let d;
    if (Array.isArray(s)) {
      const [y, m, day, H = 0, M = 0, S = 0] = s;
      d = new Date(y, m - 1, day, H, M, S);
    } else {
      d = new Date(s);
    }
    if (isNaN(d.getTime())) return String(s);
    const pad = (n) => n.toString().padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  } catch { return String(s); }
};
</script>

<template>
  <FrontendLayout>
    <div class="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- Add Address Modal -->
      <div v-if="showAddAddressModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
        <div class="bg-white rounded-3xl border border-[#e4e2de] p-6 max-w-md w-full shadow-2xl">
          <h3 class="text-sm font-bold text-stone-800 mb-4 font-display flex items-center gap-1.5 border-b border-stone-100 pb-3">
            <Plus class="w-4.5 h-4.5 text-[#ff6b35]" /><span>新增收货地址</span>
          </h3>
          <div class="space-y-4">
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">收货人姓名 *</label>
              <input type="text" v-model="addrName" placeholder="请输入姓名" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]" />
            </div>
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">手机号码 *</label>
              <input type="text" v-model="addrPhone" placeholder="请输入手机号" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]" />
            </div>
            <div class="grid grid-cols-3 gap-3">
              <div><label class="block text-[11px] font-bold text-stone-500 mb-1">省份</label><input type="text" v-model="addrProvince" class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de]" /></div>
              <div><label class="block text-[11px] font-bold text-stone-500 mb-1">城市</label><input type="text" v-model="addrCity" class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de]" /></div>
              <div><label class="block text-[11px] font-bold text-stone-500 mb-1">区县</label><input type="text" v-model="addrDistrict" class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de]" /></div>
            </div>
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">详细地址 *</label>
              <input type="text" v-model="addrDetail" placeholder="街区、楼牌号、门牌号" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]" />
            </div>
            <div class="flex items-center gap-3">
              <label class="text-[11px] font-bold text-stone-500">标签:</label>
              <div class="flex gap-1.5">
                <button v-for="tag in ['家', '公司', '学校', '其他']" :key="tag" @click="addrTag = tag" :class="`px-3 py-1 text-[11px] rounded-lg font-bold border ${addrTag === tag ? 'bg-[#ff6b35] text-white border-transparent' : 'border-[#e4e2de] text-[#5a4136]'}`">{{ tag }}</button>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <input type="checkbox" id="chk-def" v-model="addrIsDefault" class="w-3.5 h-3.5 accent-[#ff6b35]" />
              <label for="chk-def" class="text-xs text-stone-600 font-medium">设为默认收货地址</label>
            </div>
          </div>
          <div class="mt-6 flex gap-3">
            <button @click="showAddAddressModal = false" class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-500 font-bold text-xs cursor-pointer">取消</button>
            <button @click="handleAddAddress" class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold text-xs rounded-xl cursor-pointer">保存</button>
          </div>
        </div>
      </div>

      <!-- Recharge Modal -->
      <div v-if="showRechargeModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
        <div class="bg-white rounded-3xl border border-[#e4e2de] p-6 max-w-sm w-full shadow-2xl">
          <div class="flex items-center justify-between border-b border-stone-100 pb-3 mb-4">
            <h3 class="text-sm font-bold text-stone-800 font-display flex items-center gap-1.5">
              <Coins class="w-4 h-4 text-[#ff6b35]" /><span>钱包充值</span>
            </h3>
            <button @click="closeRechargeModal" class="p-1 rounded-full text-stone-400 hover:bg-stone-100">
              <span class="material-symbols-outlined text-lg">close</span>
            </button>
          </div>

          <div class="bg-[#ffebe3] border border-[#ffdbcc] rounded-xl p-3 mb-4 text-[11px] text-[#ff6b35] flex items-start gap-2">
            <ShieldCheck class="w-4 h-4 shrink-0 mt-0.5" />
            <span>当前余额 ￥{{ walletBalance.toFixed(2) }}。点击确认后将跳转支付宝沙箱完成付款，单笔限额 ￥100,000。</span>
          </div>

          <div class="mb-3">
            <label class="block text-[11px] font-bold text-stone-500 mb-1">充值金额 *</label>
            <div class="relative">
              <span class="absolute left-3 top-1/2 -translate-y-1/2 text-xs text-stone-400 font-bold">￥</span>
              <input
                type="number"
                v-model.number="rechargeAmount"
                min="1"
                step="1"
                placeholder="请输入金额"
                class="w-full text-sm pl-7 pr-3 py-2.5 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] font-mono"
              />
            </div>
          </div>

          <div class="flex flex-wrap gap-1.5 mb-4">
            <button
              v-for="amt in [50, 100, 200, 500, 1000]"
              :key="amt"
              type="button"
              @click="rechargeAmount = amt"
              :class="`px-3 py-1.5 text-[11px] font-bold rounded-lg border transition-colors cursor-pointer ${
                rechargeAmount === amt
                  ? 'bg-[#ff6b35] text-white border-transparent'
                  : 'border-[#e4e2de] text-stone-600 hover:bg-[#ffebe3] hover:border-[#ffdbcc]'
              }`"
            >
              ￥{{ amt }}
            </button>
          </div>

          <div class="flex gap-3">
            <button @click="closeRechargeModal" :disabled="rechargeSubmitting" class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-500 font-bold text-xs cursor-pointer">取消</button>
            <button @click="handleRecharge" :disabled="rechargeSubmitting" class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 disabled:bg-stone-300 text-white font-bold text-xs rounded-xl cursor-pointer">
              {{ rechargeSubmitting ? '充值中...' : '确认充值' }}
            </button>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        <!-- Left -->
        <div class="lg:col-span-4 flex flex-col gap-6">
          <div v-if="userStore.user" class="bg-white border border-[#e4e2de] rounded-3xl p-6 shadow-sm">
            <div class="text-center pb-5 border-b border-stone-100">
              <div class="mx-auto inline-block">
                <UserAvatar :user="userStore.user" size="xl" />
              </div>
              <h2 class="text-lg font-black text-stone-800 font-display mt-3">{{ userStore.user.nickname || userStore.user.username }}</h2>
              <div class="inline-flex items-center gap-1.5 px-3 py-1 bg-[#ffdbcc] text-[#ff6b35] text-[10px] font-bold rounded-full mt-2">
                <ShieldCheck class="w-3.5 h-3.5" /><span>{{ userStore.user.rank }}</span>
              </div>
            </div>

            <div class="grid grid-cols-2 gap-4 py-5 border-b border-stone-100">
              <div class="text-center border-r border-stone-100">
                <span class="block text-[10px] text-stone-400 font-semibold mb-1">会员钱包</span>
                <span class="text-lg font-black text-stone-800 font-mono">￥{{ walletBalance.toFixed(2) }}</span>
                <button @click="openRechargeModal" class="mt-2 text-[10px] text-white bg-[#004e64] hover:bg-[#004e64]/90 px-3 py-1 rounded-lg font-bold flex items-center gap-0.5 mx-auto cursor-pointer shadow-sm active:scale-95 transition-all">
                  <Coins class="w-3 h-3" /><span>充值</span>
                </button>
                <button @click="router.push('/wallet-logs')" class="mt-1.5 text-[10px] text-stone-500 hover:text-[#ff6b35] font-semibold flex items-center gap-0.5 mx-auto cursor-pointer transition-colors">
                  查看流水 <ChevronRight class="w-3 h-3" />
                </button>
              </div>
              <div class="text-center">
                <span class="block text-[10px] text-stone-400 font-semibold mb-1">成长值 / 积分</span>
                <span class="text-lg font-black text-stone-800 font-mono">{{ userStore.user.growthValue || 0 }} / {{ userStore.user.points || 0 }}</span>
                <p class="text-[9px] text-stone-400 mt-2 font-medium">100积分抵￥1</p>
              </div>
            </div>

            <div class="pt-5 space-y-3.5 text-xs text-stone-600">
              <div class="flex items-center justify-between"><span class="text-stone-400">会员账号</span><span class="font-bold text-stone-700 font-mono">{{ userStore.user.username }}</span></div>
              <div class="flex items-center justify-between"><span class="text-stone-400">绑定手机</span><span class="font-bold text-stone-700 font-mono">{{ userStore.user.phone }}</span></div>
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-1 text-stone-400"><Calendar class="w-4 h-4 shrink-0" /><span>加入时间</span></div>
                <span class="font-bold text-stone-700 font-mono">{{ formatDate(userStore.user.joinDate) }}</span>
              </div>
            </div>
          </div>

          <div class="bg-white border border-[#e4e2de] rounded-3xl p-6 shadow-sm">
            <h3 class="text-xs font-bold text-stone-400 uppercase tracking-widest font-mono mb-4">快捷功能导航</h3>
            <div class="grid grid-cols-2 gap-2.5">
              <button @click="router.push('/order')" class="flex flex-col items-center justify-center p-3 rounded-2xl bg-[#f5f3ef] hover:bg-[#ffdbcc]/40 border border-[#e4e2de] hover:border-[#ff6b35]/20 text-stone-750 hover:text-stone-900 transition-all cursor-pointer text-xs font-bold">
                <span class="material-symbols-outlined text-[#ff6b35] text-lg mb-1">shopping_bag</span><span>我的订单</span>
              </button>
              <button @click="router.push('/favorites')" class="flex flex-col items-center justify-center p-3 rounded-2xl bg-[#f5f3ef] hover:bg-[#ffdbcc]/40 border border-[#e4e2de] hover:border-[#ff6b35]/20 text-stone-750 hover:text-stone-900 transition-all cursor-pointer text-xs font-bold">
                <span class="material-symbols-outlined text-[#ff6b35] text-lg mb-1">favorite</span><span>我的收藏</span>
              </button>
              <button @click="router.push('/coupons')" class="flex flex-col items-center justify-center p-3 rounded-2xl bg-[#f5f3ef] hover:bg-[#ffdbcc]/40 border border-[#e4e2de] hover:border-[#ff6b35]/20 text-stone-750 hover:text-stone-900 transition-all cursor-pointer text-xs font-bold">
                <span class="material-symbols-outlined text-[#ff6b35] text-lg mb-1">confirmation_number</span><span>领券中心</span>
              </button>
              <button @click="router.push('/address')" class="flex flex-col items-center justify-center p-3 rounded-2xl bg-[#f5f3ef] hover:bg-[#ffdbcc]/40 border border-[#e4e2de] hover:border-[#ff6b35]/20 text-stone-750 hover:text-stone-900 transition-all cursor-pointer text-xs font-bold">
                <span class="material-symbols-outlined text-[#ff6b35] text-lg mb-1">location_on</span><span>收货地址</span>
              </button>
              <button @click="router.push('/settings')" class="flex flex-col items-center justify-center p-3 rounded-2xl bg-[#f5f3ef] hover:bg-[#ffdbcc]/40 border border-[#e4e2de] hover:border-[#ff6b35]/20 text-stone-750 hover:text-stone-900 transition-all cursor-pointer text-xs font-bold col-span-2">
                <span class="material-symbols-outlined text-[#ff6b35] text-lg mb-1">manage_accounts</span><span>账号设置</span>
              </button>
              <button @click="handleLogout" class="flex items-center justify-center gap-1.5 p-2.5 rounded-2xl border border-red-150 hover:bg-red-50 hover:text-red-600 text-stone-500 transition-all cursor-pointer text-xs font-semibold col-span-2 mt-2">
                <span class="material-symbols-outlined text-sm">logout</span><span>切换或退出账户</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Right -->
        <div class="lg:col-span-8 flex flex-col gap-6">
          <div class="flex border-b border-[#e4e2de] bg-[#efeeea] p-1 rounded-2xl w-fit">
            <button @click="activeTab = 'orders'" :class="`flex items-center gap-1.5 px-4.5 py-2.5 text-xs font-bold rounded-xl transition-all cursor-pointer ${activeTab === 'orders' ? 'bg-white text-stone-900 shadow-sm' : 'text-[#5a4136] hover:text-[#1b1c1a]'}`">
              <ShoppingBag class="w-4 h-4 shrink-0" /><span>近期订单</span>
            </button>
            <button @click="activeTab = 'addresses'" :class="`flex items-center gap-1.5 px-4.5 py-2.5 text-xs font-bold rounded-xl transition-all cursor-pointer ${activeTab === 'addresses' ? 'bg-white text-stone-900 shadow-sm' : 'text-[#5a4136] hover:text-[#1b1c1a]'}`">
              <MapPin class="w-4 h-4 shrink-0" /><span>收货地址</span>
            </button>
            <button @click="activeTab = 'coupons'" :class="`flex items-center gap-1.5 px-4.5 py-2.5 text-xs font-bold rounded-xl transition-all cursor-pointer ${activeTab === 'coupons' ? 'bg-white text-stone-900 shadow-sm' : 'text-[#5a4136] hover:text-[#1b1c1a]'}`">
              <Ticket class="w-4 h-4 shrink-0" /><span>我的优惠券</span>
            </button>
            <button @click="activeTab = 'recent-views'" :class="`flex items-center gap-1.5 px-4.5 py-2.5 text-xs font-bold rounded-xl transition-all cursor-pointer ${activeTab === 'recent-views' ? 'bg-white text-stone-900 shadow-sm' : 'text-[#5a4136] hover:text-[#1b1c1a]'}`">
              <History class="w-4 h-4 shrink-0" /><span>近期浏览</span>
            </button>
          </div>

          <div class="bg-white border border-[#e4e2de] rounded-3xl p-6 shadow-sm min-h-[400px]">
            <!-- Orders -->
            <div v-if="activeTab === 'orders'" class="space-y-4">
              <div class="flex justify-between items-center border-b border-stone-100 pb-3">
                <h4 class="text-xs font-bold text-stone-400 uppercase tracking-widest font-mono">最近 5 笔订单</h4>
                <button @click="router.push('/order')" class="text-[11px] text-[#ff6b35] hover:underline font-bold flex items-center gap-0.5">
                  查看全部 <ChevronRight class="w-3.5 h-3.5" />
                </button>
              </div>
              <div v-if="orders.length === 0" class="text-center py-16 text-stone-400">
                <span class="material-symbols-outlined text-stone-300 text-5xl">order_approve</span>
                <p class="text-xs text-stone-500 mt-2">您暂时还没有任何订单历史哦</p>
              </div>
              <div v-for="order in orders" :key="order.id" class="border border-[#e4e2de] rounded-2xl p-4 hover:border-stone-300 transition-colors text-xs">
                <div class="flex justify-between items-center mb-3">
                  <span class="font-mono text-stone-500">#{{ order.id }} · {{ formatDate(order.createdAt) }}</span>
                  <span class="px-2.5 py-0.5 text-[10px] font-bold rounded-md bg-stone-50 text-stone-500">{{ statusMap[order.status] || order.status }}</span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-stone-500">实付金额</span>
                  <span class="font-black text-[#ff6b35] font-display">￥{{ Number(order.totalPrice ?? order.payAmount ?? order.totalAmount ?? 0).toFixed(2) }}</span>
                </div>
                <button v-if="order.status === 'PENDING_RECEIVE'" @click="handleConfirmDelivery(order.id)" class="mt-3 w-full py-2 bg-[#004e64] hover:bg-[#004e64]/90 text-white font-bold rounded-xl text-[11px]">确认收货</button>
              </div>
            </div>

            <!-- Addresses（与 /address 独立页共用同一 store 数据；点"设为默认"立即生效） -->
            <div v-else-if="activeTab === 'addresses'" class="space-y-4">
              <div class="flex justify-between items-center border-b border-stone-100 pb-3 mb-2">
                <h4 class="text-xs font-bold text-stone-400 uppercase tracking-widest font-mono">收货地址列表</h4>
                <button @click="showAddAddressModal = true" class="text-xs bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold py-1.5 px-3.5 rounded-xl flex items-center gap-1 cursor-pointer">
                  <Plus class="w-3.5 h-3.5" /><span>添加新地址</span>
                </button>
              </div>
              <div v-if="userStore.addresses.length === 0" class="text-center py-20 text-stone-400">
                <span class="material-symbols-outlined text-stone-300 text-5xl">home_pin</span>
                <p class="text-xs text-stone-500 mt-2">暂无地址，请新增地址</p>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div v-for="addr in userStore.addresses" :key="addr.id" class="p-4 rounded-2xl border border-[#e4e2de] hover:border-[#ff6b35]/30 hover:shadow-sm transition-all bg-white relative flex flex-col justify-between">
                  <div>
                    <div class="flex items-center justify-between mb-2">
                      <span class="text-xs font-bold text-stone-800">{{ addr.consignee || addr.name || '收货人' }}</span>
                      <span class="text-[10px] px-2 py-0.5 bg-[#efeeea] text-[#5a4136] rounded-md font-bold font-mono">{{ addr.tag || '家' }}</span>
                    </div>
                    <p class="text-xs text-stone-600 font-medium leading-none">{{ addr.phone }}</p>
                    <p class="text-[11px] text-stone-400 mt-2 leading-relaxed">
                      {{ addr.province || '' }} {{ addr.city || '' }} {{ addr.district || '' }} {{ addr.detail || '' }}
                    </p>
                  </div>
                  <div class="mt-4 pt-3 border-t border-stone-100 flex justify-between items-center">
                    <span v-if="addr.isDefault" class="text-[9px] font-bold text-[#ff6b35] bg-[#ffdbcc] px-2 py-0.5 rounded-md">默认地址</span>
                    <button v-else @click="handleSetDefault(addr)" class="text-[10px] text-stone-500 hover:text-[#ff6b35] font-bold cursor-pointer">设为默认</button>
                    <button @click="userStore.deleteAddress(addr.id)" class="p-1 text-stone-400 hover:text-red-500 rounded hover:bg-red-50 cursor-pointer" title="删除地址">
                      <Trash2 class="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 近期浏览（F-U-XXX 按最近浏览时间倒序，去重 productId） -->
            <div v-else-if="activeTab === 'recent-views'" class="space-y-4">
              <div class="flex items-center justify-between border-b border-stone-100 pb-3 mb-2">
                <h4 class="text-xs font-bold text-stone-400 uppercase tracking-widest font-mono">近期浏览商品</h4>
                <span class="text-[10px] text-stone-400">最近 20 条</span>
              </div>
              <div v-if="recentViewsLoading" class="text-center py-20 text-stone-400">
                <span class="material-symbols-outlined animate-spin text-3xl">progress_activity</span>
                <p class="text-xs mt-2">加载中...</p>
              </div>
              <div v-else-if="recentViews.length === 0" class="text-center py-20 text-stone-400">
                <History class="w-12 h-12 text-stone-300 mx-auto mb-2" />
                <p class="text-xs text-stone-500">暂无浏览记录，去逛逛商品吧</p>
              </div>
              <div v-else class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                <div
                  v-for="p in recentViews"
                  :key="p.id"
                  @click="router.push(`/product/${p.id}`)"
                  class="group bg-white border border-[#e4e2de] rounded-2xl overflow-hidden hover:shadow-md hover:border-[#ff6b35]/30 transition-all cursor-pointer flex flex-col"
                >
                  <div class="aspect-square w-full overflow-hidden bg-stone-100">
                    <img :src="p.coverImage" :alt="p.name" class="w-full h-full object-cover transform transition-transform duration-500 group-hover:scale-105" />
                  </div>
                  <div class="p-3 flex-1 flex flex-col justify-between">
                    <h5 class="text-xs font-bold text-stone-800 line-clamp-2 leading-snug group-hover:text-[#ff6b35] transition-colors">{{ p.name }}</h5>
                    <div class="mt-2 flex items-baseline gap-1.5">
                      <span class="text-[#ff6b35] text-sm font-black font-display">￥{{ Number(p.price).toFixed(1) }}</span>
                      <span v-if="p.marketPrice && Number(p.marketPrice) > Number(p.price)" class="text-stone-400 text-[10px] line-through">￥{{ Number(p.marketPrice).toFixed(1) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Coupons -->
            <div v-else-if="activeTab === 'coupons'" class="space-y-4">
              <h4 class="text-xs font-bold text-stone-400 uppercase tracking-widest font-mono border-b border-stone-100 pb-3 mb-2">优惠券清单</h4>
              <div v-if="userStore.coupons.length === 0" class="text-center py-20 text-stone-400">
                <span class="material-symbols-outlined text-stone-300 text-5xl">receipt_long</span>
                <p class="text-xs text-stone-500 mt-2">您还没有拥有任何优惠券哦</p>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div v-for="coupon in userStore.coupons" :key="coupon.id" class="border border-[#e4e2de] rounded-2xl overflow-hidden hover:shadow-sm transition-shadow bg-white flex">
                  <div class="bg-gradient-to-br from-[#ff6b35] to-[#ffdbcc] text-white p-5 flex flex-col justify-center items-center shrink-0 min-w-[90px]">
                    <span class="text-2xl font-black font-display leading-none">
                      <template v-for="(c, i) in [couponFace(coupon.type, coupon.discount)]" :key="i">
                        {{ c.prefix }}{{ c.value }}<span class="text-sm">{{ c.suffix }}</span>
                      </template>
                    </span>
                    <span class="text-[9px] font-bold uppercase mt-1 opacity-90">满￥{{ coupon.minSpend }}</span>
                  </div>
                  <div class="p-4 flex-1 flex flex-col justify-between">
                    <div>
                      <h5 class="text-xs font-bold text-stone-800 leading-tight">{{ coupon.name || coupon.couponName || '优惠券' }}</h5>
                      <p class="text-[10px] text-stone-400 mt-1 leading-normal">{{ coupon.description }}</p>
                    </div>
                    <div class="flex justify-between items-center text-[9px] text-stone-400 mt-2 font-mono">
                      <span>{{ coupon.expiryDate || coupon.endDate || '' }}</span>
                      <span class="px-2 py-0.5 rounded-md font-bold bg-green-50 text-green-600">已领取</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </FrontendLayout>
</template>
