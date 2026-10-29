<script setup>
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { setAdminToken } from "@/api/http";
import { KeyRound, ShieldCheck, ArrowRight, UserCheck, Eye, EyeOff } from "lucide-vue-next";

const router = useRouter();
const route = useRoute();

const username = ref("admin");
const password = ref("admin123");
const showPassword = ref(false);
const errorMsg = ref("");
const isLoading = ref(false);

const handleAdminLogin = async () => {
  if (!username.value || !password.value) {
    errorMsg.value = "请填写账号与密码";
    return;
  }
  isLoading.value = true;
  errorMsg.value = "";
  try {
    const res = await fetch("http://localhost:8080/api/admin/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: username.value, password: password.value }),
    });
    const data = await res.json();
    if (data.code === 200) {
      setAdminToken(data.data.token);
      const back = route.query.redirect || "/admin";
      router.push(back);
    } else {
      errorMsg.value = data.msg || "授权失败";
    }
  } catch (e) {
    errorMsg.value = "网络错误：" + e.message;
  } finally {
    isLoading.value = false;
  }
};

const handleAutofill = () => {
  username.value = "admin";
  password.value = "admin123";
};
</script>

<template>
  <div class="min-h-screen bg-[#1b1c1a] text-[#fbf9f5] flex flex-col justify-center py-12 sm:px-6 lg:px-8 font-sans antialiased">
    <div class="sm:mx-auto sm:w-full sm:max-w-md">
      <div class="flex flex-col items-center gap-2 text-center">
        <span class="w-12 h-12 rounded-2xl bg-gradient-to-br from-[#ff6b35] to-[#ffebe3] flex items-center justify-center text-[#1b1c1a] font-black text-2xl shadow-lg">
          M
        </span>
        <h2 class="mt-4 text-center text-xl font-bold tracking-tight text-white font-display">
          麦乐迪系统控制中心
        </h2>
        <p class="text-[10px] text-stone-400 font-mono tracking-widest uppercase mt-1">
          MELODY CONTROL PORTAL (EMPLOYEES ONLY)
        </p>
      </div>
    </div>

    <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
      <div class="bg-[#2a2b29] py-8 px-6 border border-[#3e3f3d] rounded-3xl sm:px-10 shadow-xl">

        <div v-if="errorMsg" class="mb-5 bg-red-950/50 border border-red-500/30 text-red-400 rounded-xl p-3.5 text-xs flex items-center gap-2">
          <span class="material-symbols-outlined text-red-400 text-base">gpp_maybe</span>
          <span>{{ errorMsg }}</span>
        </div>

        <form class="space-y-6" @submit.prevent="handleAdminLogin">
          <div>
            <label for="admin-username" class="block text-[11px] font-bold text-stone-400 mb-1.5 uppercase tracking-wider">管理员账号</label>
            <div class="relative">
              <input
                id="admin-username"
                type="text"
                v-model="username"
                required
                placeholder="请输入管理员账号..."
                class="w-full text-xs pl-10 pr-3.5 py-2.5 rounded-xl bg-[#1b1c1a] border border-[#3e3f3d] focus:outline-none focus:border-[#ff6b35] text-white shadow-sm font-mono"
              />
              <span class="material-symbols-outlined text-stone-500 text-base absolute left-3.5 top-1/2 -translate-y-1/2">admin_panel_settings</span>
            </div>
          </div>

          <div>
            <label for="admin-password" class="block text-[11px] font-bold text-stone-400 mb-1.5 uppercase tracking-wider">安全口令</label>
            <div class="relative">
              <input
                id="admin-password"
                :type="showPassword ? 'text' : 'password'"
                v-model="password"
                required
                placeholder="请输入密码..."
                class="w-full text-xs pl-10 pr-10 py-2.5 rounded-xl bg-[#1b1c1a] border border-[#3e3f3d] focus:outline-none focus:border-[#ff6b35] text-white shadow-sm font-mono"
              />
              <KeyRound class="w-4 h-4 text-stone-500 absolute left-3.5 top-1/2 -translate-y-1/2" />

              <button
                type="button"
                @click="showPassword = !showPassword"
                class="absolute right-3.5 top-1/2 -translate-y-1/2 text-stone-500 hover:text-white"
              >
                <Eye v-if="!showPassword" class="w-4 h-4" />
                <EyeOff v-else class="w-4 h-4" />
              </button>
            </div>
          </div>

          <div class="flex items-center justify-between text-xs text-stone-400">
            <span class="text-[10px] bg-red-500/10 text-red-400 font-bold px-2 py-0.5 rounded border border-red-500/20 flex items-center gap-1">
              <ShieldCheck class="w-3.5 h-3.5 animate-pulse" /> SSL加密链路已激活
            </span>

            <button type="button" @click="handleAutofill" class="text-[#ff6b35] hover:underline font-bold">
              一键填充演示号
            </button>
          </div>

          <button
            type="submit"
            :disabled="isLoading"
            class="w-full py-3 px-4 rounded-xl bg-[#ff6b35] text-white hover:bg-[#ff6b35]/90 font-bold text-xs transition-all flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-50 shadow-md"
          >
            <span v-if="isLoading">验证凭证中...</span>
            <span v-else class="flex items-center gap-1">
              <span>进入后台系统</span>
              <ArrowRight class="w-3.5 h-3.5" />
            </span>
          </button>
        </form>

        <div class="mt-6 border-t border-[#3e3f3d] pt-6 text-center text-xs text-stone-400">
          <span>非员工入口？</span>
          <a @click="router.push('/')" class="text-[#ff6b35] hover:underline font-bold cursor-pointer">
            回到前台商城
          </a>
        </div>

      </div>
    </div>
  </div>
</template>