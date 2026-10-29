<script setup>
import { ref, nextTick, computed, onMounted } from "vue";
import { Bot, Send, X, RotateCcw, Sparkles } from "lucide-vue-next";
import {
  getAgentSessionId,
  fetchChatHistory,
  newAgentSession,
  streamChat,
} from "@/api/agent";

const open = ref(false);
const panel = ref(null);
const input = ref("");
const loading = ref(false);
const historyLoaded = ref(false);
const errorMessage = ref("");
const sessionId = ref("");

// 消息列表：{role:'user'|'assistant', content} | {role:'tool', label}
const messages = ref([]);

const isLoggedIn = computed(() => !!localStorage.getItem("access_token"));

const QUICK_QUESTIONS = [
  "牛肉干多少钱？",
  "优惠券怎么用？",
  "帮我看下我的订单",
  "怎么申请退款？",
];

const scrollToEnd = () => {
  nextTick(() => {
    const el = panel.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
};

const loadHistory = async () => {
  if (historyLoaded.value) return;
  historyLoaded.value = true;
  try {
    const rows = await fetchChatHistory(sessionId.value);
    const list = [];
    for (const r of rows) {
      if (r.role === "user") list.push({ role: "user", content: r.content });
      else if (r.role === "assistant" && r.content)
        list.push({ role: "assistant", content: r.content });
    }
    if (list.length) {
      messages.value = list;
      scrollToEnd();
    }
  } catch (_) {
    /* 服务未启动等情况静默，用户发消息时再提示 */
  }
};

const toggle = () => {
  open.value = !open.value;
  if (open.value) {
    sessionId.value = getAgentSessionId();
    loadHistory();
    scrollToEnd();
  }
};

const newChat = async () => {
  try {
    sessionId.value = await newAgentSession();
    messages.value = [];
    errorMessage.value = "";
  } catch (e) {
    errorMessage.value = "新建会话失败，请确认智能助手服务已启动";
  }
};

const send = async (text) => {
  const content = (text || input.value).trim();
  if (!content || loading.value) return;
  input.value = "";
  errorMessage.value = "";
  messages.value.push({ role: "user", content });
  const assistantMsg = { role: "assistant", content: "" };
  messages.value.push(assistantMsg);
  loading.value = true;
  scrollToEnd();

  let lastWasTool = false;
  await streamChat({
    sessionId: sessionId.value,
    message: content,
    onDelta: (t) => {
      assistantMsg.content += t;
      scrollToEnd();
    },
    onTool: (ev) => {
      if (lastWasTool) {
        // 连续工具调用：更新提示条
        const last = messages.value[messages.value.length - 2];
        if (last && last.role === "tool") last.label = ev.label || ev.name;
      } else {
        const idx = messages.value.indexOf(assistantMsg);
        messages.value.splice(idx, 0, { role: "tool", label: ev.label || ev.name });
      }
      lastWasTool = true;
      scrollToEnd();
    },
    onDone: () => {
      if (!assistantMsg.content)
        assistantMsg.content = "（本轮没有生成回复，请重试）";
    },
    onError: (msg) => {
      errorMessage.value = msg;
    },
  });

  if (!assistantMsg.content && errorMessage.value)
    messages.value = messages.value.filter((m) => m !== assistantMsg);
  loading.value = false;
  scrollToEnd();
};

onMounted(() => {
  sessionId.value = getAgentSessionId();
});
</script>

<template>
  <!-- 悬浮球 -->
  <button
    v-show="!open"
    @click="toggle"
    class="fixed bottom-6 right-6 z-50 w-14 h-14 rounded-full bg-gradient-to-br from-[#ff6b35] to-[#ff9a3c] text-white shadow-lg shadow-orange-500/30 flex items-center justify-center hover:scale-105 active:scale-95 transition-transform cursor-pointer"
    title="智能购物助手"
  >
    <Bot class="w-7 h-7" />
    <span
      class="absolute -top-1 -right-1 px-1.5 py-0.5 text-[9px] font-black bg-[#1b1c1a] rounded-full tracking-wider"
      >AI</span
    >
  </button>

  <!-- 聊天面板 -->
  <div
    v-show="open"
    class="fixed bottom-6 right-6 z-50 w-[min(92vw,380px)] h-[min(72vh,560px)] bg-white rounded-2xl shadow-2xl border border-[#e4e2de] flex flex-col overflow-hidden animate-in"
  >
    <!-- 头部 -->
    <div
      class="px-4 py-3 bg-gradient-to-r from-[#ff6b35] to-[#ff9a3c] text-white flex items-center justify-between shrink-0"
    >
      <div class="flex items-center gap-2.5">
        <div
          class="w-8 h-8 rounded-xl bg-white/20 flex items-center justify-center"
        >
          <Bot class="w-5 h-5" />
        </div>
        <div class="leading-tight">
          <div class="text-sm font-bold">小乐 · 智能购物助手</div>
          <div class="text-[10px] text-white/80">查价 · 下单 · 规则问答</div>
        </div>
      </div>
      <div class="flex items-center gap-1">
        <button
          @click="newChat"
          title="新开对话"
          class="p-1.5 rounded-lg hover:bg-white/20 transition-colors cursor-pointer"
        >
          <RotateCcw class="w-4 h-4" />
        </button>
        <button
          @click="open = false"
          title="收起"
          class="p-1.5 rounded-lg hover:bg-white/20 transition-colors cursor-pointer"
        >
          <X class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- 未登录提示 -->
    <div
      v-if="!isLoggedIn"
      class="px-4 py-1.5 bg-amber-50 border-b border-amber-100 text-[11px] text-amber-700 shrink-0"
    >
      未登录：可查商品价格与平台规则；加购/下单请先登录商城
    </div>

    <!-- 消息区 -->
    <div ref="panel" class="flex-1 overflow-y-auto px-3.5 py-3 space-y-2.5 bg-[#fbf9f5]">
      <!-- 欢迎与快捷问题 -->
      <div v-if="messages.length === 0" class="pt-6 text-center">
        <div
          class="w-12 h-12 mx-auto rounded-2xl bg-gradient-to-br from-[#ff6b35] to-[#ffdbcc] flex items-center justify-center text-white mb-3"
        >
          <Sparkles class="w-6 h-6" />
        </div>
        <p class="text-sm font-semibold text-stone-700">你好，我是小乐 👋</p>
        <p class="text-xs text-stone-500 mt-1 px-6">
          可以问我商品价格、帮我下单、查订单，也可以咨询优惠券、退款等平台规则
        </p>
        <div class="flex flex-wrap gap-2 justify-center mt-4 px-2">
          <button
            v-for="q in QUICK_QUESTIONS"
            :key="q"
            @click="send(q)"
            class="text-xs px-3 py-1.5 rounded-full bg-white border border-[#e4e2de] text-stone-600 hover:border-[#ff6b35] hover:text-[#ff6b35] transition-colors cursor-pointer"
          >
            {{ q }}
          </button>
        </div>
      </div>

      <template v-for="(m, i) in messages" :key="i">
        <!-- 工具状态提示 -->
        <div v-if="m.role === 'tool'" class="flex items-center gap-1.5 px-2">
          <span
            class="w-1.5 h-1.5 rounded-full bg-teal-500 animate-pulse"
          ></span>
          <span class="text-[11px] text-stone-400">{{ m.label }}</span>
        </div>

        <!-- 用户消息 -->
        <div v-else-if="m.role === 'user'" class="flex justify-end">
          <div
            class="max-w-[80%] text-[13px] leading-relaxed px-3.5 py-2 rounded-2xl rounded-br-md bg-[#ff6b35] text-white whitespace-pre-wrap break-words"
          >
            {{ m.content }}
          </div>
        </div>

        <!-- 助手消息 -->
        <div v-else class="flex justify-start">
          <div
            class="max-w-[85%] text-[13px] leading-relaxed px-3.5 py-2 rounded-2xl rounded-bl-md bg-white border border-[#e4e2de] text-stone-700 whitespace-pre-wrap break-words"
          >
            {{ m.content
            }}<span
              v-if="loading && i === messages.length - 1 && m.content"
              class="inline-block w-0.5 h-3.5 bg-[#ff6b35] ml-0.5 animate-pulse align-middle"
            ></span>
          </div>
        </div>
      </template>

      <!-- 思考中 -->
      <div
        v-if="loading && messages[messages.length - 1]?.content === ''"
        class="flex items-center gap-1.5 px-2"
      >
        <span class="text-[11px] text-stone-400">小乐思考中</span>
        <span class="flex gap-0.5">
          <span
            class="w-1.5 h-1.5 rounded-full bg-stone-300 animate-bounce"
            style="animation-delay: 0ms"
          ></span>
          <span
            class="w-1.5 h-1.5 rounded-full bg-stone-300 animate-bounce"
            style="animation-delay: 120ms"
          ></span>
          <span
            class="w-1.5 h-1.5 rounded-full bg-stone-300 animate-bounce"
            style="animation-delay: 240ms"
          ></span>
        </span>
      </div>

      <!-- 错误提示 -->
      <div
        v-if="errorMessage"
        class="text-[11px] text-red-500 bg-red-50 border border-red-100 rounded-lg px-3 py-2"
      >
        {{ errorMessage }}
      </div>
    </div>

    <!-- 输入区 -->
    <div class="p-2.5 border-t border-[#e4e2de] bg-white shrink-0">
      <div class="flex items-end gap-2">
        <textarea
          v-model="input"
          rows="1"
          placeholder="输入问题，如：辣条多少钱？"
          class="flex-1 resize-none text-[13px] px-3 py-2 rounded-xl border border-[#e4e2de] bg-[#fbf9f5] focus:outline-none focus:ring-2 focus:ring-[#ff6b35]/30 focus:border-[#ff6b35] max-h-24 placeholder:text-stone-400"
          @keydown.enter.exact.prevent="send()"
        />
        <button
          @click="send()"
          :disabled="loading || !input.trim()"
          class="w-9 h-9 rounded-xl bg-[#ff6b35] text-white flex items-center justify-center hover:bg-[#ff5714] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shrink-0"
        >
          <Send class="w-4 h-4" />
        </button>
      </div>
      <p class="text-[10px] text-stone-400 mt-1.5 text-center">
        AI 生成内容仅供参考 · 下单前会向你确认订单摘要
      </p>
    </div>
  </div>
</template>

<style scoped>
.animate-in {
  animation: pop-in 0.18s ease-out;
}
@keyframes pop-in {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
