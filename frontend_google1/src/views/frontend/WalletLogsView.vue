<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { fetchWalletLogs, fetchWalletBalance } from "@/api/user";
import FrontendLayout from "@/components/FrontendLayout.vue";
import Pagination from "@/components/Pagination.vue";
import {
  ArrowLeft,
  Coins,
  TrendingUp,
  TrendingDown,
  RefreshCcw,
  ShoppingBag,
  Wallet,
} from "lucide-vue-next";

const router = useRouter();

const logs = ref([]);
const balance = ref(0);
const loading = ref(false);
const typeFilter = ref("ALL");

const typeMap = {
  RECHARGE:      { label: "充值",     icon: RefreshCcw,  color: "text-emerald-600 bg-emerald-50",       isIncome: true  },
  PAY:           { label: "支付",     icon: ShoppingBag, color: "text-stone-500 bg-stone-100",           isIncome: false },
  REFUND:        { label: "退款",     icon: TrendingUp,  color: "text-blue-600 bg-blue-50",             isIncome: true  },
  ADMIN_ADJUST:  { label: "管理员调整", icon: Wallet,    color: "text-amber-600 bg-amber-50",           isIncome: null  },
};

const filteredLogs = computed(() => {
  if (typeFilter.value === "ALL") return logs.value;
  return logs.value.filter(l => l.type === typeFilter.value);
});

const currentPage = ref(1);
const pageSize = 15;
const pagedLogs = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredLogs.value.slice(start, start + pageSize);
});
watch(typeFilter, () => { currentPage.value = 1; });

// 汇总：本页内的总入账 / 总出账
const summary = computed(() => {
  let income = 0, expense = 0;
  for (const l of logs.value) {
    const amt = Number(l.changeAmount || 0);
    if (amt > 0) income += amt;
    else if (amt < 0) expense += Math.abs(amt);
  }
  return { income, expense };
});

const formatMoney = (v) => {
  const n = Number(v || 0);
  return `${n > 0 ? "+" : ""}${n.toFixed(2)}`;
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
  } catch {
    return String(s);
  }
};

async function loadAll() {
  loading.value = true;
  try {
    const [list, bal] = await Promise.all([
      fetchWalletLogs().catch(() => []),
      fetchWalletBalance().catch(() => null),
    ]);
    logs.value = list || [];
    balance.value = Number(bal?.balance ?? 0);
  } finally {
    loading.value = false;
  }
}

onMounted(loadAll);
</script>

<template>
  <FrontendLayout>
    <div class="max-w-[820px] mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <div class="flex items-center gap-3 mb-6">
        <button
          @click="router.push('/profile')"
          class="p-2 bg-white border border-[#e4e2de] hover:bg-[#efeeea] rounded-xl text-stone-600 transition-colors cursor-pointer"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-xl font-bold tracking-tight text-stone-800 font-display">钱包流水</h1>
          <p class="text-xs text-stone-500 mt-0.5">查看充值 / 支付 / 退款等钱包变动记录</p>
        </div>
      </div>

      <!-- 余额 + 汇总 -->
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-5">
        <div class="bg-gradient-to-br from-[#ff6b35] to-[#ff8f66] text-white rounded-2xl p-5 shadow-sm">
          <div class="flex items-center gap-2 mb-2">
            <Coins class="w-4 h-4" />
            <span class="text-[11px] font-bold opacity-90">当前余额</span>
          </div>
          <div class="text-2xl font-black font-display">￥{{ balance.toFixed(2) }}</div>
        </div>
        <div class="bg-white border border-[#e4e2de] rounded-2xl p-5 shadow-sm">
          <div class="flex items-center gap-2 mb-2 text-emerald-600">
            <TrendingUp class="w-4 h-4" />
            <span class="text-[11px] font-bold">累计入账</span>
          </div>
          <div class="text-2xl font-black text-emerald-600 font-display">+￥{{ summary.income.toFixed(2) }}</div>
        </div>
        <div class="bg-white border border-[#e4e2de] rounded-2xl p-5 shadow-sm">
          <div class="flex items-center gap-2 mb-2 text-stone-500">
            <TrendingDown class="w-4 h-4" />
            <span class="text-[11px] font-bold">累计出账</span>
          </div>
          <div class="text-2xl font-black text-stone-700 font-display">-￥{{ summary.expense.toFixed(2) }}</div>
        </div>
      </div>

      <!-- 类型筛选 -->
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-3 mb-5 shadow-sm flex flex-wrap gap-1.5">
        <button
          v-for="t in [
            { value: 'ALL', label: '全部' },
            { value: 'RECHARGE', label: '充值' },
            { value: 'PAY', label: '支付' },
            { value: 'REFUND', label: '退款' },
            { value: 'ADMIN_ADJUST', label: '管理员调整' }
          ]"
          :key="t.value"
          @click="typeFilter = t.value"
          :class="`px-3.5 py-1.5 text-xs font-bold rounded-xl transition-all cursor-pointer ${
            typeFilter === t.value ? 'bg-[#ff6b35] text-white shadow-sm' : 'text-[#5a4136] hover:bg-[#efeeea]'
          }`"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- 列表 -->
      <div class="bg-white border border-[#e4e2de] rounded-2xl overflow-hidden shadow-sm">
        <div v-if="loading" class="p-12 text-center text-stone-400">
          <span class="material-symbols-outlined text-stone-300 text-4xl animate-spin inline-block">progress_activity</span>
          <p class="text-xs mt-2">加载中…</p>
        </div>
        <div v-else-if="filteredLogs.length === 0" class="p-12 text-center text-stone-400">
          <Wallet class="w-12 h-12 text-stone-300 mx-auto mb-3" />
          <p class="text-sm font-bold text-stone-600">暂无流水记录</p>
          <p class="text-xs mt-1">充值或下单后会自动产生记录</p>
        </div>
        <div v-else class="divide-y divide-stone-100">
          <div
            v-for="log in pagedLogs"
            :key="log.id"
            class="p-4 flex items-center justify-between gap-4 hover:bg-[#fbf9f5] transition-colors"
          >
            <div class="flex items-center gap-3 min-w-0">
              <div
                :class="`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
                  (typeMap[log.type] || typeMap.PAY).color
                }`"
              >
                <component :is="(typeMap[log.type] || typeMap.PAY).icon" class="w-5 h-5" />
              </div>
              <div class="min-w-0">
                <div class="text-sm font-bold text-stone-800">
                  {{ (typeMap[log.type] || { label: log.type }).label }}
                  <span v-if="log.relatedOrderId" class="text-[10px] font-mono text-stone-400 ml-1">
                    订单 #{{ log.relatedOrderId }}
                  </span>
                </div>
                <div class="text-[11px] text-stone-400 mt-0.5 truncate">
                  {{ log.reason || log.operator || '—' }}
                </div>
                <div class="text-[10px] text-stone-400 font-mono mt-0.5">
                  {{ formatDate(log.logTime || log.createdAt) }}
                </div>
              </div>
            </div>
            <div class="text-right shrink-0">
              <div
                :class="`text-base font-black font-display ${
                  Number(log.changeAmount) >= 0 ? 'text-emerald-600' : 'text-stone-700'
                }`"
              >
                {{ formatMoney(log.changeAmount) }}
              </div>
              <div class="text-[10px] text-stone-400 font-mono mt-0.5">
                余额 ￥{{ Number(log.balanceAfter || 0).toFixed(2) }}
              </div>
            </div>
          </div>
        </div>
      </div>
      <Pagination
        :total="filteredLogs.length"
        v-model:page="currentPage"
        :size="pageSize"
      />
    </div>
  </FrontendLayout>
</template>