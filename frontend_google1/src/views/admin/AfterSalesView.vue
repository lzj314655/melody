<script setup>
import { ref, computed, onMounted } from "vue";
import AdminLayout from "@/components/AdminLayout.vue";
import {
  fetchAfterSaleTickets,
  fetchAfterSaleTicket,
  decideAfterSaleTicket,
} from "@/api/afterSales";
import {
  ShieldAlert,
  RefreshCw,
  Search,
  ChevronRight,
  CheckCircle2,
  XCircle,
  Clock3,
  Bot,
} from "lucide-vue-next";

const tickets = ref([]);
const loading = ref(false);
const refreshing = ref(false);
const selectedStatus = ref("ALL");
const keyword = ref("");
const selected = ref(null);
const detailLoading = ref(false);
const submitting = ref(false);

const statusMap = {
  PENDING_REVIEW: { label: "待审核", style: "bg-amber-50 text-amber-700 border-amber-200" },
  APPROVED: { label: "已通过", style: "bg-blue-50 text-blue-700 border-blue-200" },
  REJECTED: { label: "已拒绝", style: "bg-rose-50 text-rose-700 border-rose-200" },
  REFUNDED: { label: "已退款", style: "bg-emerald-50 text-emerald-700 border-emerald-200" },
  CLOSED: { label: "已关闭", style: "bg-stone-100 text-stone-500 border-stone-200" },
};

const riskMap = {
  LOW: "bg-emerald-50 text-emerald-700 border-emerald-200",
  MEDIUM: "bg-amber-50 text-amber-700 border-amber-200",
  HIGH: "bg-rose-50 text-rose-700 border-rose-200",
};

const intentMap = {
  LOGISTICS: "物流",
  QUALITY: "质量",
  REFUND: "退款",
  MALICIOUS: "恶意投诉",
  OTHER: "其他",
};

const statusOptions = ["ALL", "PENDING_REVIEW", "APPROVED", "REJECTED", "REFUNDED", "CLOSED"];

const formatDate = (s) => {
  if (!s) return "—";
  try {
    const d = new Date(String(s).replace(" ", "T"));
    if (isNaN(d.getTime())) return String(s);
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  } catch {
    return String(s);
  }
};

const money = (v) => `￥${Number(v || 0).toFixed(2)}`;

const load = async () => {
  loading.value = true;
  try {
    tickets.value = (await fetchAfterSaleTickets("ALL")) || [];
  } catch (e) {
    alert("加载售后工单失败：" + (e.message || e));
    tickets.value = [];
  } finally {
    loading.value = false;
  }
};

const refresh = async () => {
  refreshing.value = true;
  try {
    await load();
  } finally {
    refreshing.value = false;
  }
};

const filtered = computed(() => {
  let list = tickets.value;
  if (selectedStatus.value !== "ALL") {
    list = list.filter((t) => t.status === selectedStatus.value);
  }
  const kw = keyword.value.trim().toLowerCase();
  if (kw) {
    list = list.filter((t) =>
      String(t.ticketNo || "").toLowerCase().includes(kw) ||
      String(t.orderId || "").includes(kw) ||
      String(t.userId || "").includes(kw) ||
      String(t.decisionReason || "").toLowerCase().includes(kw)
    );
  }
  return list;
});

const pendingCount = computed(() =>
  tickets.value.filter((t) => t.status === "PENDING_REVIEW").length
);

const openDetail = async (t) => {
  selected.value = t;
  detailLoading.value = true;
  try {
    selected.value = await fetchAfterSaleTicket(t.id);
  } catch (e) {
    alert("加载工单详情失败：" + (e.message || e));
  } finally {
    detailLoading.value = false;
  }
};

const decide = async (action) => {
  if (!selected.value || submitting.value) return;
  const defaultReason = action === "APPROVE" ? "材料齐全，同意退款" : "不符合售后政策";
  const reason = window.prompt(
    action === "APPROVE" ? "通过原因（可选）" : "拒绝原因（会展示给用户）",
    defaultReason
  );
  if (reason === null) return;
  submitting.value = true;
  try {
    const updated = await decideAfterSaleTicket(selected.value.id, {
      action,
      reason: reason || defaultReason,
      amount: selected.value.refundAmount,
    });
    selected.value = updated;
    await load();
  } catch (e) {
    alert("审核失败：" + (e.message || e));
  } finally {
    submitting.value = false;
  }
};

onMounted(load);
</script>

<template>
  <AdminLayout
    title="售后工作台"
    subtitle="查看多 Agent 售后工单，处理高风险退款与投诉"
    :breadcrumbs="['售后工作台']"
  >
    <template #actions>
      <button
        @click="refresh"
        :disabled="refreshing"
        class="text-xs bg-[#efeeea] hover:bg-stone-200 text-[#5a4136] font-bold py-2 px-4 rounded-xl flex items-center gap-1 cursor-pointer disabled:opacity-60"
      >
        <RefreshCw :class="refreshing ? 'animate-spin' : ''" class="w-3.5 h-3.5" />
        <span>{{ refreshing ? "刷新中…" : "刷新" }}</span>
      </button>
    </template>

    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-5">
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-4">
        <p class="text-[10px] text-stone-400 font-bold">待审核</p>
        <p class="text-2xl font-black text-amber-600 font-display mt-1">{{ pendingCount }}</p>
      </div>
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-4">
        <p class="text-[10px] text-stone-400 font-bold">工单总数</p>
        <p class="text-2xl font-black text-stone-800 font-display mt-1">{{ tickets.length }}</p>
      </div>
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-4">
        <p class="text-[10px] text-stone-400 font-bold">已退款</p>
        <p class="text-2xl font-black text-emerald-600 font-display mt-1">
          {{ tickets.filter(t => t.status === 'REFUNDED').length }}
        </p>
      </div>
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-4">
        <p class="text-[10px] text-stone-400 font-bold">高风险</p>
        <p class="text-2xl font-black text-rose-600 font-display mt-1">
          {{ tickets.filter(t => t.riskLevel === 'HIGH').length }}
        </p>
      </div>
    </div>

    <div class="mb-4 flex flex-wrap gap-1.5 bg-[#efeeea] p-1 rounded-xl w-full md:w-fit">
      <button
        v-for="s in statusOptions"
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
      <div class="relative w-full sm:w-96">
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索工单号 / 订单 ID / 用户 ID / 决策原因…"
          class="w-full text-xs pl-8 pr-3 py-2.5 rounded-xl bg-[#f5f3ef] border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] text-stone-800"
        />
        <Search class="w-4 h-4 text-stone-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
      </div>
      <div class="text-xs text-stone-500">
        共 <strong class="text-stone-800 font-mono">{{ filtered.length }}</strong> 个工单
      </div>
    </div>

    <div v-if="loading" class="text-center py-16 text-xs text-stone-400">正在加载售后工单…</div>
    <div
      v-else-if="filtered.length === 0"
      class="text-center py-20 bg-[#fbf9f5] border border-dashed border-[#e4e2de] rounded-2xl"
    >
      <ShieldAlert class="w-8 h-8 text-stone-300 mx-auto mb-2" />
      <p class="text-xs text-stone-500">当前没有该状态的售后工单</p>
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="t in filtered"
        :key="t.id"
        class="bg-white border border-[#e4e2de] rounded-2xl p-4 hover:border-stone-300 transition-colors cursor-pointer"
        @click="openDetail(t)"
      >
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="flex items-center gap-3 min-w-0">
            <div class="w-10 h-10 rounded-xl bg-[#fff0e8] flex items-center justify-center text-[#ff6b35] shrink-0">
              <Bot class="w-5 h-5" />
            </div>
            <div class="min-w-0">
              <p class="text-sm font-bold text-stone-800 font-mono truncate">{{ t.ticketNo }}</p>
              <p class="text-[10px] text-stone-400 mt-0.5">
                订单 #{{ t.orderId }} · 用户 #{{ t.userId }} · {{ formatDate(t.createdAt) }}
              </p>
            </div>
          </div>
          <div class="flex items-center gap-2 flex-wrap">
            <span class="text-[10px] px-2 py-1 rounded-md border border-stone-200 bg-stone-50 text-stone-600">
              {{ intentMap[t.intent] || t.intent || '其他' }}
            </span>
            <span :class="`text-[10px] px-2 py-1 rounded-md border font-bold ${riskMap[t.riskLevel] || 'bg-stone-50 text-stone-500 border-stone-200'}`">
              {{ t.riskLevel || 'UNKNOWN' }} {{ t.riskScore ?? 0 }}
            </span>
            <span :class="`text-[10px] px-2 py-1 rounded-md border font-bold ${statusMap[t.status]?.style || 'bg-stone-50 text-stone-500 border-stone-200'}`">
              {{ statusMap[t.status]?.label || t.status }}
            </span>
            <ChevronRight class="w-4 h-4 text-stone-300" />
          </div>
        </div>
        <p class="mt-3 text-xs text-stone-500 line-clamp-2">
          {{ t.decisionReason || '暂无决策原因' }}
        </p>
      </div>
    </div>

    <!-- 详情 / 审核 -->
    <div
      v-if="selected"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
      @click.self="selected = null"
    >
      <div class="bg-white rounded-3xl border border-[#e4e2de] w-full max-w-2xl max-h-[88vh] overflow-y-auto shadow-2xl">
        <div class="flex items-center justify-between px-6 py-4 border-b border-stone-100 sticky top-0 bg-white z-10">
          <div>
            <h3 class="text-sm font-bold text-stone-800 font-display">售后工单详情</h3>
            <p class="text-[10px] text-stone-400 font-mono mt-0.5">{{ selected.ticketNo }}</p>
          </div>
          <button @click="selected = null" class="p-1.5 rounded-full text-stone-400 hover:bg-stone-100">✕</button>
        </div>

        <div v-if="detailLoading" class="p-10 text-center text-xs text-stone-400">加载中…</div>
        <div v-else class="p-6 space-y-4 text-xs">
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div class="bg-[#fbf9f5] rounded-xl p-3">
              <p class="text-[9px] text-stone-400 font-bold">状态</p>
              <p class="mt-1 font-bold text-stone-800">{{ statusMap[selected.status]?.label || selected.status }}</p>
            </div>
            <div class="bg-[#fbf9f5] rounded-xl p-3">
              <p class="text-[9px] text-stone-400 font-bold">风险等级</p>
              <p class="mt-1 font-bold text-stone-800">{{ selected.riskLevel }} / {{ selected.riskScore }}</p>
            </div>
            <div class="bg-[#fbf9f5] rounded-xl p-3">
              <p class="text-[9px] text-stone-400 font-bold">退款金额</p>
              <p class="mt-1 font-bold text-[#ff6b35]">{{ money(selected.refundAmount) }}</p>
            </div>
            <div class="bg-[#fbf9f5] rounded-xl p-3">
              <p class="text-[9px] text-stone-400 font-bold">意图</p>
              <p class="mt-1 font-bold text-stone-800">{{ intentMap[selected.intent] || selected.intent }}</p>
            </div>
          </div>

          <div class="bg-[#fbf9f5] rounded-xl p-4 space-y-2">
            <p class="text-[10px] text-stone-400 font-bold">工单信息</p>
            <p><span class="text-stone-400">订单：</span>#{{ selected.orderId }}</p>
            <p><span class="text-stone-400">用户：</span>#{{ selected.userId }}</p>
            <p><span class="text-stone-400">决策：</span>{{ selected.decision || '—' }}</p>
            <p><span class="text-stone-400">决策原因：</span>{{ selected.decisionReason || '—' }}</p>
            <p><span class="text-stone-400">LangGraph thread：</span><span class="font-mono">{{ selected.threadId || '—' }}</span></p>
            <p><span class="text-stone-400">处理人：</span>{{ selected.assignee || '—' }}</p>
          </div>

          <div v-if="selected.reply" class="bg-emerald-50/60 border border-emerald-100 rounded-xl p-4">
            <p class="text-[10px] text-emerald-700 font-bold mb-1">Agent 回复草稿</p>
            <p class="text-stone-700 leading-relaxed whitespace-pre-wrap">{{ selected.reply }}</p>
          </div>

          <div v-if="selected.status === 'PENDING_REVIEW'" class="flex gap-3 pt-2">
            <button
              @click="decide('REJECT')"
              :disabled="submitting"
              class="flex-1 py-2.5 border border-rose-200 text-rose-600 hover:bg-rose-50 disabled:opacity-50 font-bold rounded-xl cursor-pointer flex items-center justify-center gap-1"
            >
              <XCircle class="w-4 h-4" /> 拒绝
            </button>
            <button
              @click="decide('APPROVE')"
              :disabled="submitting"
              class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 disabled:opacity-50 text-white font-bold rounded-xl cursor-pointer flex items-center justify-center gap-1"
            >
              <CheckCircle2 class="w-4 h-4" /> 通过并退款
            </button>
          </div>

          <div v-else class="flex items-center gap-2 text-[10px] text-stone-400">
            <Clock3 class="w-3.5 h-3.5" />
            该工单已处理，无需重复审核
          </div>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>
