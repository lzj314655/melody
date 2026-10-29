<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { useAdminStore } from "@/store/admin";
import AdminLayout from "@/components/AdminLayout.vue";
import Pagination from "@/components/Pagination.vue";
import { Search, ShoppingBag, Truck, Check, AlertCircle, RefreshCw, ClipboardCheck, ChevronRight } from "lucide-vue-next";

const router = useRouter();
const adminStore = useAdminStore();
const searchKeyword = ref("");
const selectedStatus = ref("ALL");
const currentPage = ref(1);
const pageSize = 10;

const activeShipOrderId = ref("");
const trackingNoInput = ref("");

const statusMap = {
  PENDING_PAY: { label: "待付款", style: "bg-amber-50 text-amber-600" },
  PENDING_SHIP: { label: "待发货", style: "bg-blue-50 text-blue-600" },
  PENDING_RECEIVE: { label: "待收货", style: "bg-orange-50 text-orange-600" },
  COMPLETED: { label: "已完成", style: "bg-green-50 text-green-600" },
  CANCELED: { label: "已取消", style: "bg-stone-100 text-stone-500" },
  AFTER_SALE: { label: "售后中", style: "bg-rose-50 text-rose-600" },
  REFUNDED: { label: "已退款", style: "bg-stone-100 text-stone-500" },
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

onMounted(async () => {
  await adminStore.loadAdminData();
});

// 刷新按钮状态：旋转动画 + 完成后短暂显示"已更新"
const refreshing = ref(false);
const justRefreshed = ref(false);
let refreshTimer = null;
const handleRefresh = async () => {
  if (refreshing.value) return;
  refreshing.value = true;
  justRefreshed.value = false;
  try {
    await adminStore.loadAdminData();
    currentPage.value = 1;
    justRefreshed.value = true;
    if (refreshTimer) clearTimeout(refreshTimer);
    refreshTimer = setTimeout(() => { justRefreshed.value = false; }, 1500);
  } catch (err) {
    alert("刷新失败：" + (err.message || err));
  } finally {
    refreshing.value = false;
  }
};

const filteredOrders = computed(() => {
  let list = adminStore.orders || [];
  if (selectedStatus.value !== "ALL") {
    list = list.filter(o => o.status === selectedStatus.value);
  }
  const kw = searchKeyword.value.trim().toLowerCase();
  if (kw) {
    list = list.filter(o =>
      String(o.id).toLowerCase().includes(kw) ||
      String(o.orderNo || "").toLowerCase().includes(kw) ||
      (o.consigneeName || "").toLowerCase().includes(kw) ||
      (o.consigneePhone || "").includes(kw)
    );
  }
  return list;
});

const pagedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredOrders.value.slice(start, start + pageSize);
});
watch([searchKeyword, selectedStatus], () => { currentPage.value = 1; });

const openShipModal = (orderId) => {
  activeShipOrderId.value = orderId;
  trackingNoInput.value = "SF" + Math.floor(Math.random() * 900000000 + 100000000);
};

const handleShipOrder = async () => {
  if (!trackingNoInput.value.trim()) {
    alert("请输入快递物流单号！");
    return;
  }
  try {
    await adminStore.shipOrder(activeShipOrderId.value, trackingNoInput.value);
    activeShipOrderId.value = "";
    alert("订单已成功配载发货，顺丰速运极速为您派送！");
  } catch (err) {
    alert(err.message || "发货失败");
  }
};

const handleApproveRefund = async (orderId) => {
  if (!confirm("确认通过该订单的退款申请？退款将原路退回用户钱包。")) return;
  try {
    await adminStore.approveRefund(orderId);
    alert("已通过退款申请并退回钱包");
  } catch (e) {
    alert(e.message || "操作失败");
  }
};

const getStatusLabel = (status) => statusMap[status]?.label || status;
const getStatusClass = (status) => {
  const base = "px-2.5 py-0.5 text-[10px] font-bold rounded-md ";
  return base + (statusMap[status]?.style || "bg-stone-50 text-stone-500");
};
</script>

<template>
  <AdminLayout
    title="订单管理"
    subtitle="查看用户下的所有美味零食订单，处理顺丰快递配载发货"
    :breadcrumbs="['订单管理']"
  >
    <template #actions>
      <button
        @click="handleRefresh"
        :disabled="refreshing"
        class="text-xs bg-[#efeeea] hover:bg-stone-200 text-[#5a4136] font-bold py-2 px-4 rounded-xl flex items-center gap-1 cursor-pointer disabled:opacity-60"
      >
        <RefreshCw :class="refreshing ? 'animate-spin' : ''" class="w-3.5 h-3.5" />
        <span>{{ refreshing ? '刷新中…' : (justRefreshed ? '已更新 ✓' : '刷新') }}</span>
      </button>
    </template>

    <div class="mb-4 flex flex-wrap gap-1.5 bg-[#efeeea] p-1 rounded-xl w-full md:w-fit">
      <button
        v-for="s in ['ALL','PENDING_PAY','PENDING_SHIP','PENDING_RECEIVE','COMPLETED','AFTER_SALE','REFUNDED','CANCELED']"
        :key="s"
        @click="selectedStatus = s"
        :class="`px-3 py-1.5 text-xs font-bold rounded-lg transition-all cursor-pointer ${
          selectedStatus === s ? 'bg-white text-stone-900 shadow-sm' : 'text-[#5a4136] hover:text-[#1b1c1a]'
        }`"
      >
        {{ s === 'ALL' ? '全部' : (statusMap[s]?.label || s) }}
      </button>
    </div>

    <div class="mb-6 flex justify-between items-center gap-4">
      <div class="relative w-full sm:w-80">
        <input
          type="text"
          v-model="searchKeyword"
          placeholder="输入订单单号或收货人手机号检索..."
          class="w-full text-xs pl-8 pr-3 py-2.5 rounded-xl bg-[#f5f3ef] border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] text-stone-800 transition-colors"
        />
        <Search class="w-4 h-4 text-stone-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
      </div>
      <div class="text-xs text-stone-500">
        共 <strong class="text-stone-800 font-mono">{{ filteredOrders.length }}</strong> 笔订单
      </div>
    </div>

    <div v-if="filteredOrders.length === 0" class="text-center py-20 bg-[#fbf9f5] border border-dashed border-[#e4e2de] rounded-2xl">
      <div class="w-12 h-12 rounded-full bg-stone-100 flex items-center justify-center mx-auto mb-2 text-stone-400">
        <ShoppingBag class="w-6 h-6" />
      </div>
      <p class="text-xs text-stone-500">商城目前没有该状态的订单</p>
    </div>

    <div v-else class="space-y-6">
      <div
        v-for="order in pagedOrders"
        :key="order.id"
        class="border border-[#e4e2de] rounded-2xl overflow-hidden hover:border-stone-300 transition-colors bg-white shadow-sm"
      >
        <div class="bg-[#fbf9f5] border-b border-[#e4e2de] px-5 py-3.5 flex flex-wrap gap-3 justify-between items-center text-xs">
          <div class="flex items-center gap-6 text-stone-500 font-mono">
            <span>下单时刻: <strong class="text-stone-800">{{ formatDate(order.createdAt) }}</strong></span>
            <span>单号: <strong class="text-stone-800 font-bold">#{{ order.id }}</strong></span>
            <span v-if="order.orderNo">业务号: <strong class="text-stone-800">{{ order.orderNo }}</strong></span>
          </div>
          <div class="flex items-center gap-3">
            <button
              @click="router.push(`/admin/orders/${order.id}`)"
              class="text-[10px] text-stone-500 hover:text-[#ff6b35] font-semibold flex items-center gap-0.5 cursor-pointer transition-colors"
            >
              查看详情 <ChevronRight class="w-3 h-3" />
            </button>
            <span :class="getStatusClass(order.status)">
              {{ getStatusLabel(order.status) }}
            </span>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-12 divide-y md:divide-y-0 md:divide-x divide-stone-100">
          <div class="md:col-span-7 p-5 space-y-4">
            <div
              v-for="(item, idx) in (order.items || [])"
              :key="idx"
              class="flex items-center justify-between gap-4 text-xs"
            >
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-11 h-11 rounded-lg bg-[#fbf9f5] border border-stone-200 flex items-center justify-center text-stone-400 text-[10px] font-mono shrink-0 overflow-hidden">
                  <img
                    v-if="item.productImage"
                    :src="item.productImage"
                    :alt="item.productName"
                    class="w-full h-full object-cover"
                  />
                  <span v-else>#{{ item.productId }}</span>
                </div>
                <div class="min-w-0">
                  <h5 class="font-bold text-stone-800 truncate leading-tight">{{ item.productName || item.specName || ('商品 #' + item.productId) }}</h5>
                  <p class="text-[10px] text-stone-400 mt-1">规格：{{ item.specName || '默认' }}</p>
                </div>
              </div>
              <div class="text-right shrink-0">
                <span class="font-bold text-stone-800">￥{{ Number(item.price || 0).toFixed(2) }}</span>
                <span class="text-stone-400 font-mono ml-2">x{{ item.quantity }}</span>
              </div>
            </div>
          </div>

          <div class="md:col-span-3 p-5 text-xs text-stone-600 flex flex-col justify-center">
            <div class="mb-1">收货人: <strong class="text-stone-800">{{ order.consigneeName }}</strong></div>
            <div class="mb-1 font-mono">联系电话: <strong class="text-stone-800">{{ order.consigneePhone }}</strong></div>
            <p class="text-[10px] text-stone-400 leading-normal line-clamp-2">
              收货地址: {{ order.consigneeAddress }}
            </p>
          </div>

          <div class="md:col-span-2 p-5 text-xs flex flex-col justify-center items-center md:items-end">
            <span class="text-[10px] text-stone-400 font-semibold mb-1">实收总金额</span>
            <span class="text-base font-black text-[#ff6b35] font-display">￥{{ Number(order.totalPrice || 0).toFixed(2) }}</span>

            <div class="mt-4 w-full space-y-2">
              <button
                v-if="order.status === 'PENDING_SHIP' && activeShipOrderId !== order.id"
                @click="openShipModal(order.id)"
                class="w-full text-center py-2 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold rounded-xl text-[10px] cursor-pointer flex items-center justify-center gap-1 shadow-sm"
              >
                <Truck class="w-3.5 h-3.5" />
                <span>一键发货 (顺丰)</span>
              </button>

              <div
                v-else-if="order.status === 'PENDING_PAY'"
                class="text-amber-500 font-bold text-center text-[10px]"
              >
                <span>待买家付款</span>
              </div>

              <div
                v-else-if="order.status === 'COMPLETED'"
                class="text-[#ff6b35] font-bold flex items-center justify-center gap-1 text-[10px]"
              >
                <Check class="w-4 h-4 text-green-500" />
                <span>订单已顺利送达</span>
              </div>

              <div
                v-else-if="order.status === 'PENDING_RECEIVE'"
                class="text-stone-400 font-semibold text-center text-[10px] space-y-1.5"
              >
                <p class="text-orange-500 font-bold">顺丰运输中</p>
                <p v-if="order.trackingNo" class="text-[9px] font-mono leading-none truncate max-w-[120px]" :title="order.trackingNo">单号: {{ order.trackingNo }}</p>
              </div>

              <div
                v-else-if="order.status === 'CANCELED'"
                class="text-stone-400 font-bold text-center text-[10px]"
              >
                <span>订单已取消</span>
              </div>

              <button
                v-if="order.status === 'AFTER_SALE'"
                @click="handleApproveRefund(order.id)"
                class="w-full text-center py-2 bg-rose-600 hover:bg-rose-700 text-white font-bold rounded-xl text-[10px] cursor-pointer flex items-center justify-center gap-1 shadow-sm"
              >
                <ClipboardCheck class="w-3.5 h-3.5" />
                <span>通过退款审核</span>
              </button>
            </div>
          </div>
        </div>

        <div
          v-if="activeShipOrderId === order.id"
          class="bg-[#ffdbcc]/10 border-t border-[#ff6b35]/10 p-4 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs"
        >
          <div class="flex items-center gap-2">
            <AlertCircle class="w-4.5 h-4.5 text-[#ff6b35] shrink-0" />
            <span class="text-stone-600">请输入或核对顺丰速运物流配送单号:</span>
          </div>

          <div class="flex gap-2 w-full sm:w-auto shrink-0">
            <input
              type="text"
              v-model="trackingNoInput"
              class="px-3.5 py-1.5 rounded-xl border border-[#e4e2de] focus:border-[#ff6b35] focus:outline-none bg-white text-stone-800 font-mono text-xs w-full sm:w-56"
            />
            <button
              @click="handleShipOrder"
              class="px-4 py-1.5 bg-[#004e64] hover:bg-[#004e64]/90 text-white font-bold rounded-xl text-xs cursor-pointer shadow-sm shrink-0"
            >
              确认发货
            </button>
            <button
              @click="activeShipOrderId = ''"
              class="px-3 py-1.5 border border-[#e4e2de] hover:bg-[#efeeea] text-stone-500 rounded-xl text-xs cursor-pointer shrink-0"
            >
              取消
            </button>
          </div>
        </div>

      </div>

      <Pagination
        :total="filteredOrders.length"
        v-model:page="currentPage"
        :size="pageSize"
      />
    </div>

  </AdminLayout>
</template>