<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import {
  fetchOrderDetail,
  cancelOrderApi,
  confirmOrderApi,
  refundApi,
} from "@/api/admin";
import { walletPayOrderApi, orderAlipayPayApi, orderAlipayQueryApi } from "@/api/user";
import FrontendLayout from "@/components/FrontendLayout.vue";
import {
  ArrowLeft,
  Truck,
  MapPin,
  ShieldCheck,
  AlertCircle,
  Clock,
  CheckCircle2,
  Package,
  XCircle,
  Receipt,
} from "lucide-vue-next";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const order = ref(null);
const loading = ref(false);
const notFound = ref(false);

const statusMap = {
  PENDING_PAY: { label: "待付款", style: "bg-amber-50 text-amber-600 border border-amber-200" },
  PENDING_SHIP: { label: "待发货", style: "bg-blue-50 text-blue-600 border border-blue-200" },
  PENDING_RECEIVE: { label: "待收货", style: "bg-orange-50 text-orange-600 border border-orange-200" },
  COMPLETED: { label: "已完成", style: "bg-green-50 text-green-600 border border-green-200" },
  AFTER_SALE: { label: "售后中", style: "bg-rose-50 text-rose-600 border border-rose-200" },
  REFUNDED: { label: "已退款", style: "bg-stone-100 text-stone-500 border border-stone-200" },
  CANCELED: { label: "已取消", style: "bg-stone-100 text-stone-500 border border-stone-200" },
};

const REFUND_REASONS = [
  { value: "商品质量问题", label: "商品质量问题" },
  { value: "不想要了", label: "不想要了" },
  { value: "发错货了", label: "发错货了" },
  { value: "其他", label: "其他" },
];

const showRefundModal = ref(false);
const refundReason = ref("商品质量问题");
const refundNote = ref("");
const refundSubmitting = ref(false);

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
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  } catch {
    return String(s);
  }
};

const formatMoney = (v) => `￥${Number(v || 0).toFixed(2)}`;

const getStatusLabel = (s) => statusMap[s]?.label || s;
const getStatusBadgeClass = (s) =>
  "px-2.5 py-1 text-[10px] font-bold rounded-full " +
  (statusMap[s]?.style || "bg-stone-50 text-stone-500 border border-stone-200");

async function loadDetail() {
  const id = route.params.id;
  if (!id) {
    notFound.value = true;
    return;
  }
  loading.value = true;
  notFound.value = false;
  try {
    const data = await fetchOrderDetail(id);
    if (!data) {
      notFound.value = true;
      order.value = null;
    } else {
      order.value = { ...data, items: data.items || [] };
    }
  } catch (e) {
    // 后端在订单不属于当前用户时会抛 404，前端统一按"找不到"处理
    notFound.value = true;
    order.value = null;
    console.error(e);
  } finally {
    loading.value = false;
  }
}

// ===== 支付宝订单支付：主动查询轮询（回跳或点支付宝支付后） =====
const walletSubmitting = ref(false);
const alipayPolling = ref(false);
let pollTimer = null;
let pollCount = 0;
const MAX_ORDER_POLLS = 24; // ~60s

const stopPolling = () => {
  if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; }
  alipayPolling.value = false;
};
const pollAlipay = async () => {
  if (!order.value || order.value.status !== "PENDING_PAY") { stopPolling(); return; }
  pollCount++;
  try {
    const res = await orderAlipayQueryApi(route.params.id);
    console.log("[order-alipay-query] poll#", pollCount, res);
    if (res?.status === "PAID") { await loadDetail(); stopPolling(); return; }
  } catch (e) { console.warn("[order-alipay-query] error", e); }
  if (pollCount >= MAX_ORDER_POLLS) { stopPolling(); return; }
  pollTimer = setTimeout(pollAlipay, 2500);
};
const startAlipayPolling = () => { stopPolling(); pollCount = 0; alipayPolling.value = true; pollAlipay(); };

onMounted(async () => {
  await userStore.loadUser().catch(() => {});
  await loadDetail();
  // 从支付宝回跳（out_trade_no/trade_no）或刚下单选支付宝（alipay=1）且待付款 → 轮询确认
  if (order.value && order.value.status === "PENDING_PAY"
      && (route.query.out_trade_no || route.query.trade_no || route.query.alipay)) {
    startAlipayPolling();
  }
});
onUnmounted(stopPolling);

// 订单进度时间线——按状态点亮已完成节点
const timeline = computed(() => {
  const o = order.value;
  if (!o) return [];
  const s = o.status;
  const nodes = [
    { key: "CREATE", label: "已下单", ts: o.createdAt, icon: Receipt },
    { key: "PAY", label: "已支付", ts: o.paidAt, icon: CheckCircle2 },
    { key: "SHIP", label: "已发货", ts: o.shippedAt, icon: Package },
    { key: "DONE", label: "已完成", ts: o.completedAt, icon: ShieldCheck },
  ];
  const stopIdx = (() => {
    switch (s) {
      case "PENDING_PAY":
        return 0;
      case "PENDING_SHIP":
        return 1;
      case "PENDING_RECEIVE":
        return 2;
      case "COMPLETED":
      case "AFTER_SALE":
      case "REFUNDED":
        return 3;
      case "CANCELED":
        return -1;
      default:
        return 0;
    }
  })();
  return nodes.map((n, i) => ({
    ...n,
    active: stopIdx >= i,
    reachable: stopIdx >= i,
  }));
});

const showTimeline = computed(() => order.value && order.value.status !== "CANCELED");

async function reloadAfterAction() {
  await loadDetail();
}

const handleWalletPay = async () => {
  if (walletSubmitting.value) return;
  walletSubmitting.value = true;
  try {
    await walletPayOrderApi(order.value.id);
    alert("订单支付成功！");
    await reloadAfterAction();
  } catch (err) {
    alert("支付失败: " + err.message);
  } finally {
    walletSubmitting.value = false;
  }
};

const handleAlipayPay = async () => {
  const payWin = window.open("", "_blank");
  try {
    const { payForm } = await orderAlipayPayApi(order.value.id);
    if (!payWin) {
      alert("请允许浏览器弹窗以打开支付宝支付页");
    } else {
      payWin.document.write(payForm);
      payWin.document.close();
    }
    startAlipayPolling();
  } catch (err) {
    if (payWin) payWin.close();
    alert("拉起支付宝失败: " + err.message);
  }
};

const handleCancelOrder = async () => {
  if (!confirm("确定要取消该订单吗？")) return;
  try {
    await cancelOrderApi(order.value.id);
    alert("订单已成功取消！");
    await reloadAfterAction();
  } catch (err) {
    alert("取消失败: " + err.message);
  }
};

const handleConfirmDelivery = async () => {
  if (!confirm("确认已收到商品吗？")) return;
  try {
    await confirmOrderApi(order.value.id);
    alert("收货成功，感谢您的支持！");
    await reloadAfterAction();
  } catch (e) {
    alert("操作失败：" + e.message);
  }
};

const openRefundModal = () => {
  refundReason.value = "商品质量问题";
  refundNote.value = "";
  showRefundModal.value = true;
};
const closeRefundModal = () => {
  showRefundModal.value = false;
  refundNote.value = "";
};

const handleSubmitRefund = async () => {
  refundSubmitting.value = true;
  try {
    const fullReason = refundNote.value.trim()
      ? `${refundReason.value}（${refundNote.value.trim()}）`
      : refundReason.value;
    await refundApi(order.value.id, fullReason);
    closeRefundModal();
    alert("退款申请已提交，订单进入售后中状态，请等待管理员审核。");
    await reloadAfterAction();
  } catch (err) {
    alert("退款申请失败：" + err.message);
  } finally {
    refundSubmitting.value = false;
  }
};
</script>

<template>
  <FrontendLayout>
    <div class="max-w-[920px] mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <div class="flex items-center gap-3 mb-6">
        <button
          @click="router.push('/order')"
          class="p-2 bg-white border border-[#e4e2de] hover:bg-[#efeeea] rounded-xl text-stone-600 transition-colors cursor-pointer"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-xl font-bold tracking-tight text-stone-800 font-display">订单详情</h1>
          <p class="text-xs text-stone-500 mt-0.5">查看订单状态、物流信息及售后进度</p>
        </div>
      </div>

      <!-- 加载中 -->
      <div
        v-if="loading"
        class="bg-white border border-[#e4e2de] rounded-3xl p-16 text-center shadow-sm"
      >
        <span class="material-symbols-outlined text-stone-300 text-5xl animate-spin">
          progress_activity
        </span>
        <p class="text-xs text-stone-500 mt-2">订单加载中...</p>
      </div>

      <!-- 找不到 -->
      <div
        v-else-if="notFound || !order"
        class="bg-white border border-[#e4e2de] rounded-3xl p-16 text-center shadow-sm"
      >
        <XCircle class="w-12 h-12 text-stone-300 mx-auto mb-3" />
        <h3 class="text-sm font-bold text-stone-700">未找到该订单</h3>
        <p class="text-xs text-stone-400 mt-1">订单可能已被删除或不属于当前账号</p>
        <button
          @click="router.push('/order')"
          class="mt-4 px-4 py-2 bg-[#ff6b35] text-white font-bold rounded-xl text-xs hover:bg-[#ff6b35]/90 cursor-pointer"
        >
          返回订单列表
        </button>
      </div>

      <template v-else>
        <!-- 状态头部 -->
        <div
          class="bg-gradient-to-br from-[#ff6b35]/8 via-white to-white border border-[#e4e2de] rounded-2xl p-6 mb-5 shadow-sm"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div class="flex items-center gap-3 mb-2">
                <span :class="getStatusBadgeClass(order.status)">
                  {{ getStatusLabel(order.status) }}
                </span>
                <span class="text-xs text-stone-500 font-mono">
                  订单号：<strong class="text-stone-700">#{{ order.orderNo || order.id }}</strong>
                </span>
              </div>
              <p class="text-xs text-stone-500">
                下单时间：<strong class="text-stone-700">{{ formatDate(order.createdAt) }}</strong>
              </p>
              <p
                v-if="order.status === 'AFTER_SALE'"
                class="mt-2 text-xs text-rose-600 flex items-center gap-1"
              >
                <AlertCircle class="w-3.5 h-3.5" />
                退款原因：{{ order.refundReason || '—' }}
                <span v-if="order.refundTime" class="text-stone-400 ml-1">
                  · {{ formatDate(order.refundTime) }}
                </span>
              </p>
              <p v-if="order.status === 'CANCELED' && order.cancelReason" class="mt-2 text-xs text-stone-500">
                取消原因：<strong class="text-stone-700">{{ order.cancelReason }}</strong>
              </p>
            </div>

            <div class="flex flex-wrap justify-end gap-2 shrink-0">
              <template v-if="order.status === 'PENDING_PAY'">
                <p v-if="alipayPolling" class="text-[11px] text-[#ff6b35] font-bold w-full mb-1 flex items-center gap-1">
                  <span class="material-symbols-outlined text-sm animate-spin">progress_activity</span>
                  正在确认支付宝支付结果…
                </p>
                <button
                  @click="handleAlipayPay"
                  class="px-4 py-2.5 bg-[#1677ff] hover:bg-[#1677ff]/90 text-white font-bold rounded-xl text-xs cursor-pointer"
                >
                  支付宝支付
                </button>
                <button
                  @click="handleWalletPay"
                  :disabled="walletSubmitting"
                  class="px-4 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 disabled:bg-stone-300 text-white font-bold rounded-xl text-xs cursor-pointer"
                >
                  {{ walletSubmitting ? '支付中...' : '余额支付' }}
                </button>
                <button
                  @click="handleCancelOrder"
                  class="px-3 py-2.5 bg-white border border-[#e4e2de] text-stone-500 hover:bg-[#efeeea] font-medium rounded-xl text-xs cursor-pointer"
                >
                  取消订单
                </button>
              </template>
              <template v-else-if="order.status === 'PENDING_RECEIVE'">
                <button
                  @click="handleConfirmDelivery"
                  class="px-4 py-2.5 bg-teal-600 hover:bg-teal-700 text-white font-bold rounded-xl text-xs cursor-pointer"
                >
                  确认收货
                </button>
                <button
                  @click="openRefundModal"
                  class="px-3 py-2.5 bg-white border border-rose-200 text-rose-600 hover:bg-rose-50 font-medium rounded-xl text-xs cursor-pointer"
                >
                  申请退款
                </button>
              </template>
              <template v-else-if="order.status === 'COMPLETED'">
                <button
                  @click="openRefundModal"
                  class="px-3 py-2.5 bg-white border border-rose-200 text-rose-600 hover:bg-rose-50 font-medium rounded-xl text-xs cursor-pointer"
                >
                  申请退款
                </button>
                <button
                  @click="router.push('/')"
                  class="px-3 py-2.5 bg-white border border-[#e4e2de] text-stone-600 hover:bg-[#fbf9f5] font-medium rounded-xl text-xs cursor-pointer"
                >
                  再次购买
                </button>
              </template>
              <button
                v-else-if="order.status === 'CANCELED' || order.status === 'REFUNDED'"
                @click="router.push('/')"
                class="px-4 py-2.5 bg-white border border-[#e4e2de] text-stone-600 hover:bg-[#fbf9f5] font-bold rounded-xl text-xs cursor-pointer"
              >
                再次购买
              </button>
              <button
                v-else-if="order.status === 'AFTER_SALE'"
                class="px-3 py-2.5 bg-stone-100 border border-stone-200 text-stone-400 font-medium rounded-xl text-xs cursor-not-allowed"
                disabled
              >
                等待管理员审核
              </button>
              <!-- 所有订单状态都可进入智能客服 -->
              <button
                @click="router.push(`/after-sales/${order.id}`)"
                class="px-3 py-2.5 bg-[#fff0e8] border border-[#ffdbcc] text-[#ff6b35] hover:bg-[#ffe4d6] font-bold rounded-xl text-xs cursor-pointer"
              >
                智能客服
              </button>
            </div>
          </div>

          <!-- 时间线 -->
          <div
            v-if="showTimeline"
            class="mt-6 pt-5 border-t border-[#e4e2de] grid grid-cols-4 gap-2 relative"
          >
            <div
              v-for="(node, idx) in timeline"
              :key="node.key"
              class="relative flex flex-col items-center text-center"
            >
              <div
                :class="`relative z-10 w-9 h-9 rounded-full flex items-center justify-center border-2 transition-colors ${
                  node.reachable
                    ? 'bg-[#ff6b35] border-[#ff6b35] text-white'
                    : 'bg-white border-stone-200 text-stone-300'
                }`"
              >
                <component :is="node.icon" class="w-4 h-4" />
              </div>
              <div
                :class="`mt-2 text-[11px] font-bold ${node.reachable ? 'text-stone-700' : 'text-stone-400'}`"
              >
                {{ node.label }}
              </div>
              <div class="text-[10px] text-stone-400 mt-0.5 font-mono">
                {{ node.ts ? formatDate(node.ts) : '—' }}
              </div>
              <div
                v-if="idx < timeline.length - 1"
                :class="`absolute top-[18px] left-1/2 w-full h-0.5 -z-0 ${
                  node.reachable && timeline[idx + 1].reachable ? 'bg-[#ff6b35]' : 'bg-stone-200'
                }`"
              ></div>
            </div>
          </div>
        </div>

        <!-- 收货地址 -->
        <div class="bg-white border border-[#e4e2de] rounded-2xl p-5 mb-5 shadow-sm">
          <div class="flex items-center gap-2 mb-3">
            <MapPin class="w-4 h-4 text-[#ff6b35]" />
            <h3 class="text-sm font-bold text-stone-800 font-display">收货信息</h3>
          </div>
          <div class="text-xs text-stone-600 leading-relaxed">
            <div class="flex items-baseline gap-2">
              <strong class="text-sm text-stone-800">{{ order.consigneeName || '—' }}</strong>
              <span class="font-mono text-stone-500">{{ order.consigneePhone || '' }}</span>
            </div>
            <p class="text-stone-500 mt-1">{{ order.consigneeAddress || '—' }}</p>
          </div>
          <div
            v-if="order.trackingNo"
            class="mt-4 flex items-center gap-2 text-[11px] text-teal-700 bg-teal-50 border border-teal-100 w-fit px-3 py-1.5 rounded-lg"
          >
            <Truck class="w-3.5 h-3.5 shrink-0" />
            <span>顺丰速运：<strong class="font-mono">{{ order.trackingNo }}</strong></span>
            <span v-if="order.shippedAt" class="text-stone-400 ml-1">
              · 发货于 {{ formatDate(order.shippedAt) }}
            </span>
          </div>
        </div>

        <!-- 商品列表 -->
        <div class="bg-white border border-[#e4e2de] rounded-2xl overflow-hidden mb-5 shadow-sm">
          <div class="bg-[#fbf9f5] border-b border-[#e4e2de] px-5 py-3 flex items-center justify-between">
            <h3 class="text-sm font-bold text-stone-800 font-display">商品明细</h3>
            <span class="text-[11px] text-stone-400">共 {{ order.items.length }} 件</span>
          </div>
          <div class="divide-y divide-stone-100">
            <div
              v-for="item in order.items"
              :key="item.id"
              class="p-5 flex items-center justify-between gap-4 text-xs hover:bg-[#fbf9f5]/40 cursor-pointer transition-colors"
              @click="router.push(`/product/${item.productId}`)"
            >
              <div class="flex items-center gap-4 min-w-0">
                <div
                  class="w-14 h-14 rounded-xl bg-[#fbf9f5] border border-stone-200 flex items-center justify-center text-stone-400 text-[10px] font-mono shrink-0 overflow-hidden"
                >
                  <img
                    v-if="item.productImage"
                    :src="item.productImage"
                    :alt="item.productName"
                    class="w-full h-full object-cover"
                  />
                  <span v-else>#{{ item.productId }}</span>
                </div>
                <div class="min-w-0">
                  <h5 class="font-bold text-stone-800 truncate leading-snug">
                    {{ item.productName || item.specName || ('商品 #' + item.productId) }}
                  </h5>
                  <p class="text-[10px] text-stone-400 mt-1">
                    规格：<span class="font-semibold">{{ item.specName || '默认' }}</span>
                  </p>
                </div>
              </div>
              <div class="text-right shrink-0">
                <div class="font-bold text-stone-800">{{ formatMoney(item.price) }}</div>
                <div class="text-stone-400 font-mono mt-1">x{{ item.quantity }}</div>
              </div>
            </div>
          </div>

          <!-- 价格汇总 -->
          <div class="bg-[#efeeea]/30 border-t border-[#e4e2de] px-5 py-4 text-xs space-y-1.5">
            <div class="flex justify-between text-stone-500">
              <span>商品总额</span>
              <span class="font-mono">{{ formatMoney(order.goodsAmount) }}</span>
            </div>
            <div v-if="Number(order.discount) > 0" class="flex justify-between text-rose-600">
              <span>优惠券优惠</span>
              <span class="font-mono">-{{ formatMoney(order.discount) }}</span>
            </div>
            <div v-if="Number(order.shippingFee) > 0" class="flex justify-between text-stone-500">
              <span>运费</span>
              <span class="font-mono">{{ formatMoney(order.shippingFee) }}</span>
            </div>
            <div class="flex justify-between items-baseline pt-2 border-t border-[#e4e2de]">
              <span class="text-stone-500">实付金额</span>
              <span class="text-base font-black text-[#ff6b35] font-display">
                {{ formatMoney(order.totalPrice) }}
              </span>
            </div>
          </div>
        </div>

        <!-- 订单信息 -->
        <div class="bg-white border border-[#e4e2de] rounded-2xl p-5 shadow-sm">
          <div class="flex items-center gap-2 mb-3">
            <Clock class="w-4 h-4 text-stone-400" />
            <h3 class="text-sm font-bold text-stone-800 font-display">订单信息</h3>
          </div>
          <dl class="grid grid-cols-1 sm:grid-cols-2 gap-y-2.5 gap-x-6 text-xs">
            <div class="flex justify-between gap-3">
              <dt class="text-stone-400 shrink-0">订单号</dt>
              <dd class="font-mono text-stone-700 truncate">{{ order.orderNo || order.id }}</dd>
            </div>
            <div class="flex justify-between gap-3">
              <dt class="text-stone-400 shrink-0">下单时间</dt>
              <dd class="font-mono text-stone-700">{{ formatDate(order.createdAt) }}</dd>
            </div>
            <div v-if="order.paidAt" class="flex justify-between gap-3">
              <dt class="text-stone-400 shrink-0">支付时间</dt>
              <dd class="font-mono text-stone-700">{{ formatDate(order.paidAt) }}</dd>
            </div>
            <div v-if="order.shippedAt" class="flex justify-between gap-3">
              <dt class="text-stone-400 shrink-0">发货时间</dt>
              <dd class="font-mono text-stone-700">{{ formatDate(order.shippedAt) }}</dd>
            </div>
            <div v-if="order.completedAt" class="flex justify-between gap-3">
              <dt class="text-stone-400 shrink-0">完成时间</dt>
              <dd class="font-mono text-stone-700">{{ formatDate(order.completedAt) }}</dd>
            </div>
            <div v-if="order.note" class="flex justify-between gap-3 sm:col-span-2">
              <dt class="text-stone-400 shrink-0">订单备注</dt>
              <dd class="text-stone-700 text-right">{{ order.note }}</dd>
            </div>
          </dl>
        </div>
      </template>
    </div>

    <!-- 退款弹窗 -->
    <div
      v-if="showRefundModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
    >
      <div class="bg-white rounded-3xl border border-[#e4e2de] p-6 max-w-md w-full shadow-2xl">
        <div class="flex items-center justify-between border-b border-stone-100 pb-3 mb-4">
          <h3 class="text-sm font-bold text-stone-800 font-display">申请退款</h3>
          <button
            @click="closeRefundModal"
            class="p-1 rounded-full text-stone-400 hover:bg-stone-100"
          >
            <span class="material-symbols-outlined text-lg">close</span>
          </button>
        </div>

        <div
          class="bg-rose-50 border border-rose-100 rounded-xl p-3 mb-4 text-[11px] text-rose-700 flex items-start gap-2"
        >
          <AlertCircle class="w-4 h-4 shrink-0 mt-0.5" />
          <span>提交后订单将进入「售后中」状态，管理员审核通过后退款将原路返回钱包余额。</span>
        </div>

        <div class="mb-3">
          <label class="block text-[11px] font-bold text-stone-500 mb-1">退款原因 *</label>
          <select
            v-model="refundReason"
            class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]"
          >
            <option v-for="r in REFUND_REASONS" :key="r.value" :value="r.value">
              {{ r.label }}
            </option>
          </select>
        </div>

        <div class="mb-3">
          <label class="block text-[11px] font-bold text-stone-500 mb-1">补充说明（可选）</label>
          <textarea
            v-model="refundNote"
            rows="3"
            placeholder="可描述具体问题..."
            class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] resize-none"
          />
        </div>

        <div class="mt-6 flex gap-3">
          <button
            @click="closeRefundModal"
            :disabled="refundSubmitting"
            class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-500 font-bold text-xs cursor-pointer"
          >
            取消
          </button>
          <button
            @click="handleSubmitRefund"
            :disabled="refundSubmitting"
            class="flex-1 py-2.5 bg-rose-600 hover:bg-rose-700 disabled:bg-stone-300 text-white font-bold text-xs rounded-xl cursor-pointer"
          >
            {{ refundSubmitting ? '提交中...' : '提交申请' }}
          </button>
        </div>
      </div>
    </div>
  </FrontendLayout>
</template>