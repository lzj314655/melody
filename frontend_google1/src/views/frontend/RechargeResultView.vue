<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { queryRechargeApi, fetchWalletBalance } from "@/api/user";
import FrontendLayout from "@/components/FrontendLayout.vue";
import { ArrowLeft, CheckCircle2, Loader2, AlertTriangle, Coins } from "lucide-vue-next";

const route = useRoute();
const router = useRouter();

const outTradeNo = ref(String(route.query.out_trade_no || ""));
const amount = ref(Number(route.query.amount || 0));
const status = ref("PENDING"); // PENDING / PAID / TIMEOUT
const balance = ref(null);
const tip = ref("正在确认支付结果…");

let timer = null;
let pollCount = 0;
const MAX_POLLS = 48; // 48 × 2.5s ≈ 120s，给沙箱登录付款留足时间

const stop = () => {
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
};

const scheduleNext = () => {
  if (timer) clearTimeout(timer);
  timer = setTimeout(poll, 2500);
};

const poll = async () => {
  if (!outTradeNo.value) {
    status.value = "TIMEOUT";
    tip.value = "订单号缺失，无法查询";
    return;
  }
  pollCount++;
  try {
    const res = await queryRechargeApi(outTradeNo.value);
    // 诊断日志：F12 Console 看每次查询返回，定位轮询问题
    console.log("[recharge-query] poll#", pollCount, res);
    if (res?.status === "PAID") {
      status.value = "PAID";
      balance.value = Number(res.balance ?? 0);
      tip.value = "充值已到账";
      stop();
      return;
    }
    if (res?.status === "CLOSED") {
      status.value = "TIMEOUT";
      tip.value = "订单已关闭";
      stop();
      return;
    }
  } catch (e) {
    console.warn("[recharge-query] error", e);
  }
  if (pollCount >= MAX_POLLS) {
    status.value = "TIMEOUT";
    tip.value = "未查询到支付结果，若已扣款点下方“重新查询”即可恢复";
    stop();
    return;
  }
  scheduleNext();
};

const startPolling = () => {
  stop();
  poll();
};

const manualRetry = () => {
  status.value = "PENDING";
  tip.value = "正在确认支付结果…";
  pollCount = 0;
  startPolling();
};

onMounted(async () => {
  startPolling();
  // 顺便刷新本地余额展示
  try {
    const w = await fetchWalletBalance();
    balance.value = Number(w?.balance ?? 0);
  } catch {}
});

onUnmounted(stop);
</script>

<template>
  <FrontendLayout>
    <div class="max-w-[520px] mx-auto px-4 sm:px-6 lg:px-8 py-10">

      <div class="flex items-center gap-3 mb-6">
        <button
          @click="router.push('/profile')"
          class="p-2 bg-white border border-[#e4e2de] hover:bg-[#efeeea] rounded-xl text-stone-600 transition-colors cursor-pointer"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <h1 class="text-xl font-bold tracking-tight text-stone-800 font-display">充值结果</h1>
      </div>

      <!-- 查询中 -->
      <div v-if="status === 'PENDING'" class="bg-white border border-[#e4e2de] rounded-2xl p-8 shadow-sm text-center">
        <Loader2 class="w-12 h-12 text-[#ff6b35] mx-auto mb-4 animate-spin" />
        <p class="text-base font-bold text-stone-800 mb-1">{{ tip }}</p>
        <p class="text-xs text-stone-500 leading-relaxed">
          请在新打开的支付宝沙箱页面完成付款，付款后系统将自动确认到账。<br />
          订单号 <span class="font-mono text-stone-400">{{ outTradeNo }}</span>
        </p>
      </div>

      <!-- 成功 -->
      <div v-else-if="status === 'PAID'" class="bg-white border border-[#e4e2de] rounded-2xl p-8 shadow-sm text-center">
        <div class="w-16 h-16 rounded-full bg-emerald-50 flex items-center justify-center mx-auto mb-4">
          <CheckCircle2 class="w-9 h-9 text-emerald-600" />
        </div>
        <p class="text-lg font-black text-stone-800 font-display mb-1">充值成功</p>
        <p v-if="amount > 0" class="text-sm text-stone-500 mb-3">
          ￥{{ amount.toFixed(2) }} 已到账
        </p>
        <div class="inline-flex items-center gap-1.5 bg-[#fff4ef] border border-[#ffe1d3] rounded-xl px-4 py-2 mb-6">
          <Coins class="w-4 h-4 text-[#ff6b35]" />
          <span class="text-sm font-bold text-stone-700">当前余额 ￥{{ (balance ?? 0).toFixed(2) }}</span>
        </div>
        <div class="flex gap-2">
          <button
            @click="router.push('/wallet-logs')"
            class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-600 font-bold text-xs hover:bg-[#efeeea] cursor-pointer transition-colors"
          >
            查看流水
          </button>
          <button
            @click="router.push('/profile')"
            class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold text-xs rounded-xl cursor-pointer transition-colors"
          >
            返回个人中心
          </button>
        </div>
      </div>

      <!-- 超时 / 失败 -->
      <div v-else class="bg-white border border-[#e4e2de] rounded-2xl p-8 shadow-sm text-center">
        <div class="w-16 h-16 rounded-full bg-amber-50 flex items-center justify-center mx-auto mb-4">
          <AlertTriangle class="w-9 h-9 text-amber-500" />
        </div>
        <p class="text-lg font-black text-stone-800 font-display mb-1">未确认到账</p>
        <p class="text-xs text-stone-500 leading-relaxed mb-6">
          {{ tip }}<br />
          订单号 <span class="font-mono text-stone-400">{{ outTradeNo }}</span>
        </p>
        <div class="flex gap-2">
          <button
            @click="manualRetry"
            class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-600 font-bold text-xs hover:bg-[#efeeea] cursor-pointer transition-colors"
          >
            重新查询
          </button>
          <button
            @click="router.push('/profile')"
            class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold text-xs rounded-xl cursor-pointer transition-colors"
          >
            返回个人中心
          </button>
        </div>
      </div>
    </div>
  </FrontendLayout>
</template>
