<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import {
  fetchMyOrders,
  cancelOrderApi,
  confirmOrderApi,
  refundApi,
} from "@/api/admin";
import FrontendLayout from "@/components/FrontendLayout.vue";
import Pagination from "@/components/Pagination.vue";
import { ShoppingBag, ArrowLeft, Search, Truck, ShieldCheck, AlertCircle } from "lucide-vue-next";

const router = useRouter();
const userStore = useUserStore();

const orders = ref([]);
const loading = ref(false);
const searchKeyword = ref("");
const selectedStatus = ref("ALL");

const statuses = [
  { value: "ALL", label: "全部" },
  { value: "PENDING_PAY", label: "待付款" },
  { value: "PENDING_SHIP", label: "待发货" },
  { value: "PENDING_RECEIVE", label: "待收货" },
  { value: "COMPLETED", label: "已完成" },
  { value: "CANCELED", label: "已取消" }
];

const statusMap = {
  PENDING_PAY: { label: "待付款", style: "bg-amber-50 text-amber-600 border border-amber-200" },
  PENDING_SHIP: { label: "待发货", style: "bg-blue-50 text-blue-600 border border-blue-200" },
  PENDING_RECEIVE: { label: "待收货", style: "bg-orange-50 text-orange-600 border border-orange-200" },
  COMPLETED: { label: "已完成", style: "bg-green-50 text-green-600 border border-green-200" },
  AFTER_SALE: { label: "售后中", style: "bg-rose-50 text-rose-600 border border-rose-200" },
  REFUNDED: { label: "已退款", style: "bg-stone-100 text-stone-500 border border-stone-200" },
  CANCELED: { label: "已取消", style: "bg-stone-100 text-stone-500 border border-stone-200" }
};

const REFUND_REASONS = [
  { value: "商品质量问题", label: "商品质量问题" },
  { value: "不想要了", label: "不想要了" },
  { value: "发错货了", label: "发错货了" },
  { value: "其他", label: "其他" }
];

const showRefundModal = ref(false);
const refundOrderId = ref(null);
const refundReason = ref("商品质量问题");
const refundNote = ref("");
const refundSubmitting = ref(false);

const openRefundModal = (orderId) => {
  refundOrderId.value = orderId;
  refundReason.value = "商品质量问题";
  refundNote.value = "";
  showRefundModal.value = true;
};

const closeRefundModal = () => {
  showRefundModal.value = false;
  refundOrderId.value = null;
  refundNote.value = "";
};

const formatDate = (s) => {
  if (!s) return "";
  // 处理后端 LocalDateTime（数组 [yyyy, MM, dd, HH, mm, ss] 或 [yyyy, MM, dd] 或 ISO 字符串）
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
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  } catch {
    return String(s);
  }
};

async function loadOrders() {
  loading.value = true;
  try {
    // 后端 /api/order 已自动把 items join 进每条 order
    const list = await fetchMyOrders("ALL");
    orders.value = (list || []).map(o => ({
      ...o,
      items: o.items || [],
    }));
  } catch (e) {
    orders.value = [];
    console.error(e);
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await userStore.loadUser().catch(() => {});
  await loadOrders();
});

const filteredOrders = computed(() => {
  let list = orders.value;
  if (selectedStatus.value !== "ALL") {
    list = list.filter(o => o.status === selectedStatus.value);
  }
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase();
    list = list.filter(o =>
      String(o.id).toLowerCase().includes(kw) ||
      (o.items || []).some(item => String(item.specName || item.productName || "").toLowerCase().includes(kw))
    );
  }
  return list;
});

const currentPage = ref(1);
const pageSize = 5;
const pagedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredOrders.value.slice(start, start + pageSize);
});
watch([selectedStatus, searchKeyword], () => { currentPage.value = 1; });

const handleConfirmDelivery = async (orderId) => {
  if (!confirm("确认已收到商品吗？")) return;
  try {
    await confirmOrderApi(orderId);
    await loadOrders();
    alert("收货成功，感谢您的支持！");
  } catch (e) {
    alert("操作失败：" + e.message);
  }
};

const handlePayOrder = (orderId) => {
  // 列表页跳详情页，在那里选余额 / 支付宝支付
  router.push(`/order/${orderId}`);
};

const handleCancelOrder = async (orderId) => {
  if (!confirm("确定要取消该订单吗？")) return;
  try {
    await cancelOrderApi(orderId);
    await loadOrders();
    alert("订单已成功取消！");
  } catch (err) {
    alert("取消失败: " + err.message);
  }
};

const handleSubmitRefund = async () => {
  if (!refundReason.value) {
    alert("请选择退款原因");
    return;
  }
  refundSubmitting.value = true;
  try {
    const fullReason = refundNote.value.trim()
      ? `${refundReason.value}（${refundNote.value.trim()}）`
      : refundReason.value;
    await refundApi(refundOrderId.value, fullReason);
    closeRefundModal();
    alert("退款申请已提交，订单进入售后中状态，请等待管理员审核。");
    await loadOrders();
  } catch (err) {
    alert("退款申请失败：" + err.message);
  } finally {
    refundSubmitting.value = false;
  }
};

const getStatusLabel = (status) => statusMap[status]?.label || status;
const getStatusBadgeClass = (status) => {
  const base = "px-2.5 py-1 text-[10px] font-bold rounded-full ";
  return base + (statusMap[status]?.style || "bg-stone-50 text-stone-500 border border-stone-200");
};
</script>

<template>
  <FrontendLayout>
    <div class="max-w-[1080px] mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <div class="flex items-center gap-3 mb-6">
        <button @click="router.push('/profile')" class="p-2 bg-white border border-[#e4e2de] hover:bg-[#efeeea] rounded-xl text-stone-600 transition-colors cursor-pointer">
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-xl font-bold tracking-tight text-stone-800 font-display">我的订单中心</h1>
          <p class="text-xs text-stone-500 mt-0.5">查看您在麦乐迪零食商场的所有订购记录及物流轨迹</p>
        </div>
      </div>

      <div class="bg-white border border-[#e4e2de] rounded-2xl p-4 shadow-sm mb-6 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div class="flex flex-wrap gap-1.5 bg-[#efeeea] p-1 rounded-xl w-full md:w-auto">
          <button
            v-for="status in statuses"
            :key="status.value"
            @click="selectedStatus = status.value"
            :class="`px-3 py-1.5 text-xs font-bold rounded-lg transition-all cursor-pointer ${
              selectedStatus === status.value ? 'bg-white text-stone-900 shadow-sm' : 'text-[#5a4136] hover:text-[#1b1c1a]'
            }`"
          >
            {{ status.label }}
          </button>
        </div>
        <div class="relative w-full md:w-72">
          <input
            type="text"
            v-model="searchKeyword"
            placeholder="搜索商品名称、订单号..."
            class="w-full text-xs pl-8 pr-3.5 py-2.5 rounded-xl bg-white border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] transition-colors"
          />
          <Search class="w-4 h-4 text-stone-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
        </div>
      </div>

      <div v-if="loading" class="bg-white border border-[#e4e2de] rounded-3xl p-16 text-center shadow-sm">
        <span class="material-symbols-outlined text-stone-300 text-5xl animate-spin">progress_activity</span>
        <p class="text-xs text-stone-500 mt-2">订单加载中...</p>
      </div>
      <div v-else-if="filteredOrders.length === 0" class="bg-white border border-[#e4e2de] rounded-3xl p-16 text-center shadow-sm">
        <ShoppingBag class="w-12 h-12 text-stone-300 mx-auto mb-3" />
        <h3 class="text-sm font-bold text-stone-700">暂无相关订单记录</h3>
        <p class="text-xs text-stone-400 mt-1">您可以回到商城挑选您心仪的美味哦~</p>
        <button @click="router.push('/')" class="mt-4 px-4 py-2 bg-[#ff6b35] text-white font-bold rounded-xl text-xs hover:bg-[#ff6b35]/90 cursor-pointer">挑选美味</button>
      </div>

      <div v-else class="space-y-6">
        <div v-for="order in pagedOrders" :key="order.id" class="bg-white border border-[#e4e2de] rounded-2xl overflow-hidden hover:shadow-sm transition-all">
          <div class="bg-[#fbf9f5] border-b border-[#e4e2de] px-5 py-4 flex flex-wrap gap-3 justify-between items-center text-xs">
            <div class="flex flex-wrap items-center gap-x-6 gap-y-1 text-stone-500 font-mono">
              <span>订单号: <strong class="text-stone-700">#{{ order.id }}</strong></span>
              <span>下单时间: <strong class="text-stone-700">{{ formatDate(order.createdAt) }}</strong></span>
            </div>
            <span :class="getStatusBadgeClass(order.status)">{{ getStatusLabel(order.status) }}</span>
            <button
              @click="router.push(`/order/${order.id}`)"
              class="ml-auto md:ml-0 text-[11px] text-stone-500 hover:text-[#ff6b35] font-semibold flex items-center gap-1 cursor-pointer transition-colors"
            >
              查看详情
              <span class="material-symbols-outlined text-[14px]">chevron_right</span>
            </button>
          </div>

          <div class="p-5 divide-y divide-stone-100">
            <div v-for="(item, idx) in order.items" :key="idx" class="py-4 first:pt-0 last:pb-0 flex items-center justify-between gap-4 text-xs">
              <div class="flex items-center gap-4 min-w-0">
                <div class="w-14 h-14 rounded-xl bg-[#fbf9f5] border border-stone-200 flex items-center justify-center text-stone-400 text-[10px] font-mono shrink-0 overflow-hidden">
                  <img
                    v-if="item.productImage"
                    :src="item.productImage"
                    :alt="item.productName"
                    class="w-full h-full object-cover"
                  />
                  <span v-else>#{{ item.productId }}</span>
                </div>
                <div class="min-w-0">
                  <h5 class="font-bold text-stone-800 truncate leading-snug cursor-pointer" @click="router.push(`/product/${item.productId}`)">
                    {{ item.productName || item.specName || ('商品 #' + item.productId) }}
                  </h5>
                  <p class="text-[10px] text-stone-400 mt-1">规格：<span class="font-semibold">{{ item.specName || '默认' }}</span></p>
                </div>
              </div>
              <div class="text-right shrink-0">
                <span class="font-bold text-stone-800">￥{{ Number(item.price || 0).toFixed(2) }}</span>
                <span class="text-stone-400 font-mono ml-2">x{{ item.quantity }}</span>
              </div>
            </div>
          </div>

          <div class="bg-[#efeeea]/20 px-5 py-4 border-t border-[#e4e2de]/60 flex flex-col md:flex-row justify-between items-start md:items-center gap-4 text-xs">
            <div class="text-stone-500 max-w-lg">
              <div class="flex items-center gap-1">
                <span class="material-symbols-outlined text-sm text-stone-400">local_shipping</span>
                <span>收货地址：<strong class="text-stone-700">{{ order.consigneeName || '—' }} ({{ order.consigneePhone || '' }})</strong></span>
              </div>
              <p v-if="order.consigneeAddress" class="text-[10px] text-stone-400 mt-1 pl-5">{{ order.consigneeAddress }}</p>
              <div v-if="order.trackingNo" class="mt-2.5 pl-5 flex items-center gap-1.5 text-[10px] text-teal-600 bg-teal-50 border border-teal-100 w-fit px-2.5 py-1 rounded-lg">
                <Truck class="w-3 h-3 shrink-0" />
                <span>顺丰速运单号：<strong class="font-mono">{{ order.trackingNo }}</strong></span>
              </div>
            </div>

            <div class="flex items-center justify-between md:justify-end gap-4 w-full md:w-auto border-t md:border-0 border-stone-200/60 pt-4 md:pt-0 shrink-0">
              <div class="text-right shrink-0">
                <span class="text-[10px] text-stone-400 block font-semibold">实付金额</span>
                <span class="text-base font-black text-[#ff6b35] font-display">￥{{ Number(order.totalPrice ?? order.payAmount ?? order.totalAmount ?? 0).toFixed(2) }}</span>
              </div>
              <div class="flex gap-2">
                <template v-if="order.status === 'PENDING_PAY'">
                  <button @click="handlePayOrder(order.id)" class="px-4 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold rounded-xl text-xs cursor-pointer">立即支付</button>
                  <button @click="handleCancelOrder(order.id)" class="px-3 py-2.5 bg-white border border-[#e4e2de] text-stone-500 hover:bg-[#efeeea] font-medium rounded-xl text-xs cursor-pointer">取消订单</button>
                </template>
                <template v-else-if="order.status === 'PENDING_RECEIVE'">
                  <button @click="handleConfirmDelivery(order.id)" class="px-4 py-2.5 bg-teal-600 hover:bg-teal-700 text-white font-bold rounded-xl text-xs cursor-pointer">确认收货</button>
                  <button @click="openRefundModal(order.id)" class="px-3 py-2.5 bg-white border border-rose-200 text-rose-600 hover:bg-rose-50 font-medium rounded-xl text-xs cursor-pointer">申请退款</button>
                </template>
                <template v-else-if="order.status === 'COMPLETED'">
                  <button @click="openRefundModal(order.id)" class="px-3 py-2.5 bg-white border border-rose-200 text-rose-600 hover:bg-rose-50 font-medium rounded-xl text-xs cursor-pointer">申请退款</button>
                  <button @click="router.push('/')" class="px-3 py-2.5 bg-white border border-[#e4e2de] text-stone-600 hover:bg-[#fbf9f5] font-medium rounded-xl text-xs cursor-pointer">再次购买</button>
                </template>
                <button v-else @click="router.push('/')" class="px-4 py-2.5 bg-white border border-[#e4e2de] text-stone-600 hover:bg-[#fbf9f5] font-bold rounded-xl text-xs cursor-pointer">再次购买</button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <Pagination
        :total="filteredOrders.length"
        v-model:page="currentPage"
        :size="pageSize"
      />
    </div>

    <div v-if="showRefundModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
      <div class="bg-white rounded-3xl border border-[#e4e2de] p-6 max-w-md w-full shadow-2xl">
        <div class="flex items-center justify-between border-b border-stone-100 pb-3 mb-4">
          <h3 class="text-sm font-bold text-stone-800 font-display">申请退款</h3>
          <button @click="closeRefundModal" class="p-1 rounded-full text-stone-400 hover:bg-stone-100">
            <span class="material-symbols-outlined text-lg">close</span>
          </button>
        </div>

        <div class="bg-rose-50 border border-rose-100 rounded-xl p-3 mb-4 text-[11px] text-rose-700 flex items-start gap-2">
          <AlertCircle class="w-4 h-4 shrink-0 mt-0.5" />
          <span>提交后订单将进入「售后中」状态，管理员审核通过后退款将原路返回钱包余额。</span>
        </div>

        <div class="mb-3">
          <label class="block text-[11px] font-bold text-stone-500 mb-1">退款原因 *</label>
          <select v-model="refundReason" class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]">
            <option v-for="r in REFUND_REASONS" :key="r.value" :value="r.value">{{ r.label }}</option>
          </select>
        </div>

        <div class="mb-3">
          <label class="block text-[11px] font-bold text-stone-500 mb-1">补充说明（可选）</label>
          <textarea v-model="refundNote" rows="3" placeholder="可描述具体问题..." class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] resize-none" />
        </div>

        <div class="mt-6 flex gap-3">
          <button @click="closeRefundModal" :disabled="refundSubmitting" class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-500 font-bold text-xs cursor-pointer">取消</button>
          <button @click="handleSubmitRefund" :disabled="refundSubmitting" class="flex-1 py-2.5 bg-rose-600 hover:bg-rose-700 disabled:bg-stone-300 text-white font-bold text-xs rounded-xl cursor-pointer">
            {{ refundSubmitting ? '提交中...' : '提交申请' }}
          </button>
        </div>
      </div>
    </div>
  </FrontendLayout>
</template>
