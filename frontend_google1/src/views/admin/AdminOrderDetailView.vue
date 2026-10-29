<script setup>
import { ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAdminStore } from "@/store/admin";
import { fetchAdminOrderDetail } from "@/api/admin";
import AdminLayout from "@/components/AdminLayout.vue";
import {
  ArrowLeft,
  Truck,
  MapPin,
  ClipboardCheck,
  AlertCircle,
  Clock,
  CheckCircle2,
  Package,
  ShieldCheck,
  XCircle,
  Receipt,
  RefreshCw,
  ShoppingBag,
} from "lucide-vue-next";

const route = useRoute();
const router = useRouter();
const adminStore = useAdminStore();

const order = ref(null);
const loading = ref(false);
const notFound = ref(false);

// 发货弹窗
const showShipModal = ref(false);
const trackingNoInput = ref("");
const shipping = ref(false);

const statusMap = {
  PENDING_PAY: { label: "待付款", style: "bg-amber-50 text-amber-600 border border-amber-200" },
  PENDING_SHIP: { label: "待发货", style: "bg-blue-50 text-blue-600 border border-blue-200" },
  PENDING_RECEIVE: { label: "待收货", style: "bg-orange-50 text-orange-600 border border-orange-200" },
  COMPLETED: { label: "已完成", style: "bg-green-50 text-green-600 border border-green-200" },
  AFTER_SALE: { label: "售后中", style: "bg-rose-50 text-rose-600 border border-rose-200" },
  REFUNDED: { label: "已退款", style: "bg-stone-100 text-stone-500 border border-stone-200" },
  CANCELED: { label: "已取消", style: "bg-stone-100 text-stone-500 border border-stone-200" },
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
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  } catch { return String(s); }
};

const formatMoney = (v) => `￥${Number(v || 0).toFixed(2)}`;
const getStatusLabel = (s) => statusMap[s]?.label || s;
const getStatusBadgeClass = (s) =>
  "px-2.5 py-1 text-[10px] font-bold rounded-full " +
  (statusMap[s]?.style || "bg-stone-50 text-stone-500 border border-stone-200");

async function loadDetail() {
  const id = route.params.id;
  if (!id) { notFound.value = true; return; }
  loading.value = true;
  notFound.value = false;
  try {
    const data = await fetchAdminOrderDetail(id);
    if (!data) { notFound.value = true; order.value = null; }
    else { order.value = { ...data, items: data.items || [] }; }
  } catch (e) {
    notFound.value = true;
    order.value = null;
    console.error(e);
  } finally {
    loading.value = false;
  }
}

const reloadAfterAction = loadDetail;

const openShipModal = () => {
  trackingNoInput.value = "SF" + Math.floor(Math.random() * 900000000 + 100000000);
  showShipModal.value = true;
};

const handleShip = async () => {
  if (!trackingNoInput.value.trim()) {
    alert("请输入快递物流单号");
    return;
  }
  shipping.value = true;
  try {
    await adminStore.shipOrder(order.value.id, trackingNoInput.value.trim());
    showShipModal.value = false;
    alert("订单已成功发货");
    await reloadAfterAction();
  } catch (e) {
    alert("发货失败：" + (e.message || e));
  } finally {
    shipping.value = false;
  }
};

const handleApproveRefund = async () => {
  if (!confirm("确认通过该订单的退款申请？退款将原路退回用户钱包。")) return;
  try {
    await adminStore.approveRefund(order.value.id);
    alert("已通过退款申请并退回钱包");
    await reloadAfterAction();
  } catch (e) {
    alert("操作失败：" + e.message);
  }
};

onMounted(loadDetail);
</script>

<template>
  <AdminLayout
    title="订单详情（后台）"
    subtitle="查看订单完整信息并执行发货 / 退款审核"
    :breadcrumbs="['订单管理', '详情']"
  >
    <template #actions>
      <button
        @click="loadDetail"
        :disabled="loading"
        class="text-xs bg-[#efeeea] hover:bg-stone-200 text-[#5a4136] font-bold py-2 px-4 rounded-xl flex items-center gap-1 cursor-pointer disabled:opacity-60"
      >
        <RefreshCw :class="loading ? 'animate-spin' : ''" class="w-3.5 h-3.5" />
        <span>{{ loading ? '刷新中…' : '刷新' }}</span>
      </button>
    </template>

    <div class="flex items-center gap-3 mb-6">
      <button
        @click="router.push('/admin/orders')"
        class="p-2 bg-white border border-[#e4e2de] hover:bg-[#efeeea] rounded-xl text-stone-600 transition-colors cursor-pointer"
      >
        <ArrowLeft class="w-4 h-4" />
      </button>
      <div>
        <h1 class="text-lg font-bold tracking-tight text-stone-800 font-display">订单详情</h1>
        <p class="text-xs text-stone-500 mt-0.5">管理员视角，可执行发货 / 退款审核</p>
      </div>
    </div>

    <div
      v-if="loading"
      class="bg-white border border-[#e4e2de] rounded-3xl p-16 text-center shadow-sm"
    >
      <span class="material-symbols-outlined text-stone-300 text-5xl animate-spin inline-block">progress_activity</span>
      <p class="text-xs text-stone-500 mt-2">订单加载中…</p>
    </div>

    <div
      v-else-if="notFound || !order"
      class="bg-white border border-[#e4e2de] rounded-3xl p-16 text-center shadow-sm"
    >
      <XCircle class="w-12 h-12 text-stone-300 mx-auto mb-3" />
      <h3 class="text-sm font-bold text-stone-700">未找到该订单</h3>
      <p class="text-xs text-stone-400 mt-1">订单可能已被删除</p>
      <button
        @click="router.push('/admin/orders')"
        class="mt-4 px-4 py-2 bg-[#ff6b35] text-white font-bold rounded-xl text-xs hover:bg-[#ff6b35]/90 cursor-pointer"
      >
        返回订单列表
      </button>
    </div>

    <template v-else>
      <!-- 状态头 + 操作 -->
      <div class="bg-gradient-to-br from-[#ffebe3] via-white to-white border border-[#e4e2de] rounded-2xl p-6 mb-5 shadow-sm">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div class="flex items-center gap-3 mb-2">
              <span :class="getStatusBadgeClass(order.status)">
                {{ getStatusLabel(order.status) }}
              </span>
              <span class="text-xs text-stone-500 font-mono">
                #{{ order.orderNo || order.id }}
              </span>
            </div>
            <p class="text-xs text-stone-500">
              下单时间：<strong class="text-stone-700">{{ formatDate(order.createdAt) }}</strong>
            </p>
            <p v-if="order.status === 'AFTER_SALE' && order.refundReason" class="mt-2 text-xs text-rose-600 flex items-center gap-1">
              <AlertCircle class="w-3.5 h-3.5" />
              退款原因：{{ order.refundReason }}
              <span v-if="order.refundTime" class="text-stone-400 ml-1">· {{ formatDate(order.refundTime) }}</span>
            </p>
            <p v-if="order.status === 'CANCELED' && order.cancelReason" class="mt-2 text-xs text-stone-500">
              取消原因：<strong class="text-stone-700">{{ order.cancelReason }}</strong>
            </p>
          </div>

          <div class="flex gap-2 shrink-0">
            <button
              v-if="order.status === 'PENDING_SHIP'"
              @click="openShipModal"
              class="px-4 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold rounded-xl text-xs cursor-pointer flex items-center gap-1.5 shadow-sm"
            >
              <Truck class="w-3.5 h-3.5" />
              <span>一键发货</span>
            </button>
            <button
              v-if="order.status === 'AFTER_SALE'"
              @click="handleApproveRefund"
              class="px-4 py-2.5 bg-rose-600 hover:bg-rose-700 text-white font-bold rounded-xl text-xs cursor-pointer flex items-center gap-1.5 shadow-sm"
            >
              <ClipboardCheck class="w-3.5 h-3.5" />
              <span>审核通过退款</span>
            </button>
            <button
              v-if="order.status === 'PENDING_RECEIVE'"
              class="px-3 py-2.5 bg-stone-100 border border-stone-200 text-stone-500 font-medium rounded-xl text-xs cursor-not-allowed"
              disabled
            >
              等待用户确认收货
            </button>
          </div>
        </div>
      </div>

      <!-- 收货信息 -->
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

      <!-- 商品明细 -->
      <div class="bg-white border border-[#e4e2de] rounded-2xl overflow-hidden mb-5 shadow-sm">
        <div class="bg-[#fbf9f5] border-b border-[#e4e2de] px-5 py-3 flex items-center justify-between">
          <h3 class="text-sm font-bold text-stone-800 font-display">商品明细</h3>
          <span class="text-[11px] text-stone-400">共 {{ order.items.length }} 件</span>
        </div>
        <div class="divide-y divide-stone-100">
          <div
            v-for="item in order.items"
            :key="item.id"
            class="p-5 flex items-center justify-between gap-4 text-xs"
          >
            <div class="flex items-center gap-4 min-w-0">
              <div class="w-14 h-14 rounded-xl bg-[#fbf9f5] border border-stone-200 flex items-center justify-center text-stone-400 text-[10px] font-mono shrink-0 overflow-hidden">
                <img v-if="item.productImage" :src="item.productImage" class="w-full h-full object-cover" />
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

        <div class="bg-[#efeeea]/30 border-t border-[#e4e2de] px-5 py-4 text-xs space-y-1.5">
          <div class="flex justify-between text-stone-500">
            <span>商品总额</span>
            <span class="font-mono">{{ formatMoney(order.goodsAmount) }}</span>
          </div>
          <div v-if="Number(order.discount) > 0" class="flex justify-between text-rose-600">
            <span>优惠</span>
            <span class="font-mono">-{{ formatMoney(order.discount) }}</span>
          </div>
          <div v-if="Number(order.shippingFee) > 0" class="flex justify-between text-stone-500">
            <span>运费</span>
            <span class="font-mono">{{ formatMoney(order.shippingFee) }}</span>
          </div>
          <div class="flex justify-between items-baseline pt-2 border-t border-[#e4e2de]">
            <span class="text-stone-500">实收金额</span>
            <span class="text-base font-black text-[#ff6b35] font-display">{{ formatMoney(order.totalPrice) }}</span>
          </div>
        </div>
      </div>

      <!-- 订单信息 -->
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-5 shadow-sm">
        <div class="flex items-center gap-2 mb-3">
          <Clock class="w-4 h-4 text-stone-400" />
          <h3 class="text-sm font-bold text-stone-800 font-display">订单流转</h3>
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

    <!-- Ship Modal -->
    <div
      v-if="showShipModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
    >
      <div class="bg-white rounded-3xl border border-[#e4e2de] p-6 max-w-sm w-full shadow-2xl">
        <h3 class="text-sm font-bold text-stone-800 mb-4 font-display flex items-center gap-1.5 border-b border-stone-100 pb-3">
          <Truck class="w-4 h-4 text-[#ff6b35]" /><span>配载发货</span>
        </h3>
        <div class="bg-blue-50 border border-blue-100 rounded-xl p-3 mb-4 text-[11px] text-blue-700 flex items-start gap-2">
          <AlertCircle class="w-4 h-4 shrink-0 mt-0.5" />
          <span>填写顺丰物流单号后提交，订单将进入「待收货」状态。</span>
        </div>
        <label class="block text-[11px] font-bold text-stone-500 mb-1">物流单号 *</label>
        <input
          v-model="trackingNoInput"
          type="text"
          placeholder="例如 SF1234567890"
          class="w-full text-xs px-3 py-2.5 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] font-mono"
        />
        <div class="mt-6 flex gap-3">
          <button
            @click="showShipModal = false"
            :disabled="shipping"
            class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-500 font-bold text-xs cursor-pointer"
          >
            取消
          </button>
          <button
            @click="handleShip"
            :disabled="shipping"
            class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 disabled:bg-stone-300 text-white font-bold text-xs rounded-xl cursor-pointer"
          >
            {{ shipping ? '发货中...' : '确认发货' }}
          </button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>