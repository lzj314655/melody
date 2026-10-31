<script setup>
import { ref, onMounted, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import FrontendLayout from "@/components/FrontendLayout.vue";
import { fetchOrderDetail } from "@/api/admin";
import { streamAfterSaleChat } from "@/api/afterSales";
import {
  ArrowLeft,
  Bot,
  CheckCircle2,
  Loader2,
  Package,
  Send,
  ShieldCheck,
  Sparkles,
  User,
} from "lucide-vue-next";

const route = useRoute();
const router = useRouter();
const orderId = Number(route.params.orderId);

const order = ref(null);
const loading = ref(true);
const messages = ref([]);
const input = ref("");
const sending = ref(false);
const steps = ref([]);
const meta = ref(null);
const chatBox = ref(null);

const statusMap = {
  PENDING_PAY: "待付款",
  PENDING_SHIP: "待发货",
  PENDING_RECEIVE: "待收货",
  COMPLETED: "已完成",
  AFTER_SALE: "售后中",
  REFUNDED: "已退款",
  CANCELED: "已取消",
};

const money = (v) => `￥${Number(v || 0).toFixed(2)}`;

const scrollBottom = async () => {
  await nextTick();
  if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight;
};

const push = (role, text) => {
  messages.value.push({ role, text });
  scrollBottom();
};

const loadOrder = async () => {
  if (!orderId) {
    loading.value = false;
    return;
  }
  try {
    order.value = await fetchOrderDetail(orderId);
  } catch (e) {
    push("system", "订单加载失败：" + (e.message || e));
  } finally {
    loading.value = false;
  }
};

const send = async () => {
  const text = input.value.trim();
  if (!text || sending.value || !orderId) return;
  input.value = "";
  push("user", text);
  steps.value = [];
  meta.value = null;
  sending.value = true;
  try {
    await streamAfterSaleChat({
      orderId,
      message: text,
      onAgent: (ev) => {
        if (!steps.value.includes(ev.label)) steps.value.push(ev.label);
        scrollBottom();
      },
      onReply: (content) => {
        if (content) push("assistant", content);
      },
      onMeta: (data) => {
        meta.value = data;
      },
      onError: (msg) => push("system", msg),
      onDone: () => {
        sending.value = false;
      },
    });
  } catch (e) {
    push("system", "售后请求失败：" + (e.message || e));
  } finally {
    sending.value = false;
  }
};

const onEnter = (e) => {
  if (e.shiftKey) return;
  e.preventDefault();
  send();
};

onMounted(async () => {
  push("assistant", "您好，我是智能客服。您可以直接描述遇到的问题，我会结合订单、风控和售后政策给出处理方案。");
  await loadOrder();
});
</script>

<template>
  <FrontendLayout>
    <div class="max-w-6xl mx-auto px-4 py-8">
      <button
        @click="router.back()"
        class="inline-flex items-center gap-1.5 text-xs font-bold text-stone-500 hover:text-[#ff6b35] mb-5 cursor-pointer"
      >
        <ArrowLeft class="w-4 h-4" /> 返回
      </button>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <!-- 订单信息 -->
        <aside class="lg:col-span-1 space-y-4">
          <div class="bg-white border border-[#e4e2de] rounded-3xl p-5">
            <div class="flex items-center gap-2 mb-4">
              <Package class="w-4 h-4 text-[#ff6b35]" />
              <h2 class="text-sm font-bold text-stone-800 font-display">售后订单</h2>
            </div>
            <div v-if="loading" class="text-xs text-stone-400 py-6 text-center">加载中…</div>
            <template v-else-if="order">
              <p class="text-[10px] text-stone-400 font-mono">{{ order.orderNo }}</p>
              <div class="flex items-center justify-between mt-2">
                <span class="text-xs font-bold text-stone-700">
                  {{ statusMap[order.status] || order.status }}
                </span>
                <span class="text-sm font-black text-[#ff6b35]">{{ money(order.totalPrice) }}</span>
              </div>
              <div class="mt-4 space-y-2">
                <div
                  v-for="(item, idx) in order.items || []"
                  :key="idx"
                  class="flex items-center justify-between text-[11px] text-stone-500"
                >
                  <span class="truncate pr-2">{{ item.productName || '商品' }} × {{ item.quantity }}</span>
                  <span class="font-mono shrink-0">{{ money(item.subtotal) }}</span>
                </div>
              </div>
              <div class="mt-4 pt-4 border-t border-stone-100 text-[10px] text-stone-400 space-y-1">
                <p>下单时间：{{ order.createdAt || '—' }}</p>
                <p v-if="order.trackingNo">物流单号：{{ order.trackingNo }}</p>
              </div>
            </template>
            <p v-else class="text-xs text-stone-400">未找到订单</p>
          </div>

          <div class="bg-[#fff7f2] border border-[#ffdbcc] rounded-3xl p-5">
            <div class="flex items-center gap-2 text-[#ff6b35] mb-2">
              <ShieldCheck class="w-4 h-4" />
              <span class="text-xs font-bold">多 Agent 售后流程</span>
            </div>
            <ul class="text-[10px] text-stone-500 leading-relaxed space-y-1.5">
              <li>1. 识别意图，确认订单与物流</li>
              <li>2. 风控评估退款历史与异常行为</li>
              <li>3. 核对售后政策与证据</li>
              <li>4. 自动退款，或转人工审核</li>
            </ul>
          </div>
        </aside>

        <!-- 对话 -->
        <section class="lg:col-span-2 bg-white border border-[#e4e2de] rounded-3xl overflow-hidden flex flex-col min-h-[560px]">
          <div class="px-5 py-3.5 border-b border-stone-100 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <div class="w-8 h-8 rounded-full bg-[#ff6b35] flex items-center justify-center text-white">
                <Bot class="w-4 h-4" />
              </div>
              <div>
                <p class="text-xs font-bold text-stone-800">小乐 · 智能客服</p>
                <p class="text-[9px] text-stone-400">订单 {{ order?.orderNo || `#${orderId}` }}</p>
              </div>
            </div>
            <span class="text-[9px] text-stone-400 flex items-center gap-1">
              <Sparkles class="w-3 h-3" /> 多 Agent 协作
            </span>
          </div>

          <div ref="chatBox" class="flex-1 overflow-y-auto px-5 py-4 space-y-3 bg-[#fbf9f5]">
            <div
              v-for="(m, idx) in messages"
              :key="idx"
              :class="m.role === 'user' ? 'flex justify-end' : 'flex justify-start'"
            >
              <div class="flex items-start gap-2 max-w-[85%]">
                <div
                  v-if="m.role !== 'user'"
                  class="w-7 h-7 rounded-full bg-white border border-stone-200 flex items-center justify-center shrink-0 mt-0.5"
                >
                  <Bot class="w-3.5 h-3.5 text-[#ff6b35]" />
                </div>
                <div
                  :class="[
                    'px-3.5 py-2.5 rounded-2xl text-[11px] leading-relaxed whitespace-pre-wrap',
                    m.role === 'user'
                      ? 'bg-[#ff6b35] text-white rounded-br-sm'
                      : m.role === 'system'
                        ? 'bg-rose-50 text-rose-600 border border-rose-100'
                        : 'bg-white text-stone-700 border border-stone-200 rounded-bl-sm'
                  ]"
                >
                  {{ m.text }}
                </div>
                <div
                  v-if="m.role === 'user'"
                  class="w-7 h-7 rounded-full bg-stone-200 flex items-center justify-center shrink-0 mt-0.5"
                >
                  <User class="w-3.5 h-3.5 text-stone-500" />
                </div>
              </div>
            </div>

            <div v-if="steps.length" class="flex justify-start">
              <div class="bg-white border border-stone-200 rounded-2xl px-3.5 py-2.5 space-y-1.5">
                <div
                  v-for="(s, idx) in steps"
                  :key="idx"
                  class="flex items-center gap-2 text-[10px]"
                  :class="idx === steps.length - 1 && sending ? 'text-[#ff6b35]' : 'text-stone-400'"
                >
                  <Loader2 v-if="idx === steps.length - 1 && sending" class="w-3 h-3 animate-spin" />
                  <CheckCircle2 v-else class="w-3 h-3 text-emerald-500" />
                  {{ s }}
                </div>
              </div>
            </div>

            <div v-if="meta?.ticketNo" class="flex justify-start">
              <div class="bg-emerald-50 border border-emerald-100 rounded-2xl px-3.5 py-2.5 text-[10px] text-emerald-700">
                工单号：<span class="font-mono font-bold">{{ meta.ticketNo }}</span>
                <span v-if="meta.decision"> · {{ meta.decision }}</span>
              </div>
            </div>
          </div>

          <div class="p-3 border-t border-stone-100 bg-white">
            <div class="flex items-end gap-2">
              <textarea
                v-model="input"
                rows="2"
                :disabled="sending || !order"
                @keydown.enter="onEnter"
                placeholder="描述您的问题，例如：收到坚果发霉了，有照片，想退款"
                class="flex-1 text-xs px-3 py-2.5 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] resize-none disabled:bg-stone-50"
              />
              <button
                @click="send"
                :disabled="sending || !input.trim() || !order"
                class="w-10 h-10 rounded-xl bg-[#ff6b35] hover:bg-[#ff6b35]/90 disabled:bg-stone-300 text-white flex items-center justify-center cursor-pointer shrink-0"
              >
                <Loader2 v-if="sending" class="w-4 h-4 animate-spin" />
                <Send v-else class="w-4 h-4" />
              </button>
            </div>
            <p class="text-[9px] text-stone-400 mt-2 text-center">
              AI 处理结果仅供参考；高风险/大额工单会转人工审核
            </p>
          </div>
        </section>
      </div>
    </div>
  </FrontendLayout>
</template>
