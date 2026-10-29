<script setup>
import { ref, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useUserStore } from "@/store/user";
import { fetchCaptchaApi } from "@/api/user";
import { KeyRound, Smartphone, ShieldCheck, ArrowRight, ShieldAlert, RefreshCw } from "lucide-vue-next";

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const phone = ref("13800138001");
const password = ref("password");
const captchaCode = ref("");
const captchaId = ref("");
const captchaSvg = ref("");
const errorMsg = ref("");
const successMsg = ref("");
const isLoading = ref(false);

/**
 * 把 4 位验证码字符渲染成 SVG(与 RegisterView 同款)
 */
function buildCaptchaSvg(code) {
  const W = 120, H = 40;
  const colors = ["#1e293b", "#dc2626", "#16a34a", "#7c3aed", "#ea580c"];
  let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`;
  svg += `<rect width="${W}" height="${H}" fill="#f5f5f4"/>`;
  for (let i = 0; i < 4; i++) {
    const x1 = Math.random() * W, y1 = Math.random() * H;
    const x2 = Math.random() * W, y2 = Math.random() * H;
    svg += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#a8a29e" stroke-width="1"/>`;
  }
  for (let i = 0; i < 30; i++) {
    const x = Math.random() * W, y = Math.random() * H;
    svg += `<circle cx="${x}" cy="${y}" r="0.8" fill="#78716c"/>`;
  }
  for (let i = 0; i < code.length; i++) {
    const ch = code[i];
    const x = 15 + i * 26 + Math.random() * 4;
    const y = 28 + Math.random() * 4;
    const rotate = -25 + Math.random() * 50;
    const color = colors[Math.floor(Math.random() * colors.length)];
    const size = 22 + Math.floor(Math.random() * 4);
    svg += `<text x="${x}" y="${y}" font-family="Georgia, serif" font-size="${size}" font-weight="bold" fill="${color}" transform="rotate(${rotate} ${x} ${y})">${ch}</text>`;
  }
  svg += `</svg>`;
  return "data:image/svg+xml;base64," + btoa(unescape(encodeURIComponent(svg)));
}

async function loadCaptcha() {
  try {
    const r = await fetchCaptchaApi();
    captchaId.value = r.id;
    captchaSvg.value = buildCaptchaSvg(r.code);
  } catch (e) {
    errorMsg.value = "验证码加载失败,请刷新重试";
  }
}

onMounted(() => {
  loadCaptcha();
});

const handleLogin = async () => {
  if (!phone.value || !password.value) {
    errorMsg.value = "请输入手机号和密码！";
    return;
  }
  if (!/^\d{11}$/.test(phone.value)) {
    errorMsg.value = "请填写正确的 11 位手机号码！";
    return;
  }
  if (password.value.length < 6) {
    errorMsg.value = "密码长度不能小于 6 位";
    return;
  }
  if (captchaCode.value.length !== 4) {
    errorMsg.value = "请输入 4 位图片验证码";
    return;
  }

  isLoading.value = true;
  errorMsg.value = "";
  successMsg.value = "";
  try {
    await userStore.login(phone.value, password.value, captchaId.value, captchaCode.value);
    successMsg.value = "登录成功！即将跳转...";
    const back = route.query.redirect ? decodeURIComponent(route.query.redirect) : "/";
    setTimeout(() => router.push(back), 600);
  } catch (err) {
    if (err.message && err.message.includes("验证码")) {
      await loadCaptcha();
      captchaCode.value = "";
    }
    errorMsg.value = "登录失败: " + err.message;
  } finally {
    isLoading.value = false;
  }
};

const useDemoAccount = () => {
  phone.value = "13800138001";
  password.value = "password";
  loadCaptcha();
  captchaCode.value = "";
};
</script>

<template>
  <div class="min-h-screen bg-[#fbf9f5] flex flex-col justify-center py-12 sm:px-6 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-md">
      <div @click="router.push('/')" class="flex flex-col items-center gap-2 cursor-pointer group">
        <span class="w-12 h-12 rounded-2xl bg-gradient-to-br from-[#ff6b35] to-[#ffdbcc] flex items-center justify-center text-white font-black text-2xl shadow-md transform transition-transform group-hover:scale-105">
          M
        </span>
        <h2 class="mt-4 text-center text-2xl font-black text-stone-850 tracking-tight font-display">
          欢迎回到麦乐迪零食商场
        </h2>
        <p class="text-xs text-stone-500 font-mono tracking-widest uppercase mt-1">
          MELODY SAVORY LOGIN
        </p>
      </div>
    </div>

    <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
      <div class="bg-white py-8 px-6 shadow-sm border border-[#e4e2de] rounded-3xl sm:px-10">

        <div v-if="errorMsg" class="mb-4 bg-red-50 border border-red-200 text-red-600 rounded-xl p-3 text-xs flex items-center gap-2">
          <ShieldAlert class="w-4 h-4 shrink-0" />
          <span>{{ errorMsg }}</span>
        </div>
        <div v-if="successMsg" class="mb-4 bg-green-50 border border-green-200 text-green-600 rounded-xl p-3 text-xs flex items-center gap-2">
          <span class="material-symbols-outlined text-green-500 text-base">check_circle</span>
          <span>{{ successMsg }}</span>
        </div>

        <form class="space-y-5" @submit.prevent="handleLogin">
          <div>
            <label for="phone" class="block text-xs font-bold text-stone-600 mb-1.5">手机号码 *</label>
            <div class="relative">
              <input
                id="phone"
                type="text"
                v-model="phone"
                required
                placeholder="请输入手机号码..."
                class="w-full text-xs pl-10 pr-3.5 py-2.5 rounded-xl bg-white border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] transition-colors shadow-sm text-stone-800"
              />
              <Smartphone class="w-4 h-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            </div>
          </div>

          <div>
            <label for="password" class="block text-xs font-bold text-stone-600 mb-1.5">登录密码 *</label>
            <div class="relative">
              <input
                id="password"
                type="password"
                v-model="password"
                required
                placeholder="请输入登录密码..."
                class="w-full text-xs pl-10 pr-3.5 py-2.5 rounded-xl bg-white border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] transition-colors shadow-sm text-stone-800"
              />
              <KeyRound class="w-4 h-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            </div>
          </div>

          <!-- 图片验证码 -->
          <div>
            <label for="captcha" class="block text-xs font-bold text-stone-600 mb-1.5">图片验证码 *</label>
            <div class="flex gap-2.5">
              <div class="relative flex-1">
                <input
                  id="captcha"
                  type="text"
                  v-model="captchaCode"
                  required
                  maxlength="4"
                  placeholder="请输入图中 4 位字符"
                  class="w-full text-xs pl-10 pr-3.5 py-2.5 rounded-xl bg-white border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] transition-colors shadow-sm text-stone-800 uppercase tracking-widest"
                />
                <ShieldCheck class="w-4 h-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              </div>
              <button
                type="button"
                @click="loadCaptcha"
                title="看不清?换一张"
                class="relative shrink-0 h-[42px] w-[120px] rounded-xl border border-[#e4e2de] bg-stone-50 hover:bg-stone-100 overflow-hidden cursor-pointer"
              >
                <img
                  v-if="captchaSvg"
                  :src="captchaSvg"
                  alt="验证码"
                  class="w-full h-full object-fill"
                />
                <span v-else class="text-xs text-stone-400">加载中...</span>
                <span class="absolute top-1 right-1 w-5 h-5 rounded-full bg-white/80 flex items-center justify-center">
                  <RefreshCw class="w-3 h-3 text-stone-500" />
                </span>
              </button>
            </div>
          </div>

          <div class="flex items-center justify-between text-xs">
            <div class="flex items-center gap-2">
              <input id="remember-me" type="checkbox" class="w-3.5 h-3.5 accent-[#ff6b35] rounded" checked />
              <label for="remember-me" class="text-stone-500 font-medium cursor-pointer">记住我的登录</label>
            </div>
            <button type="button" @click="useDemoAccount" class="text-[#ff6b35] hover:underline font-bold">
              使用演示账号
            </button>
          </div>

          <button
            type="submit"
            :disabled="isLoading"
            class="w-full py-3 px-4 rounded-xl bg-[#ff6b35] text-white hover:bg-[#ff6b35]/90 focus:outline-none focus:ring-2 focus:ring-[#ff6b35]/20 font-bold text-xs transition-all flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-50"
          >
            <span v-if="isLoading">登录中...</span>
            <span v-else class="flex items-center gap-1">
              <span>立即登录</span>
              <ArrowRight class="w-3.5 h-3.5" />
            </span>
          </button>
        </form>

        <div class="mt-6 border-t border-stone-100 pt-6 text-center text-xs text-stone-500">
          <span>还没有账号？</span>
          <a @click="router.push('/register')" class="text-[#ff6b35] hover:underline font-bold cursor-pointer">
            免费注册新账号
          </a>
        </div>
      </div>
    </div>
  </div>
</template>