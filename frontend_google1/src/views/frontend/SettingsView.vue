<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import FrontendLayout from "@/components/FrontendLayout.vue";
import { ArrowLeft, User, ShieldCheck, Mail, Phone, Settings, Save, Sparkles } from "lucide-vue-next";

const router = useRouter();
const userStore = useUserStore();

const nickname = ref("");
const phone = ref("");
const email = ref("");
const avatar = ref("");
const acceptPromo = ref(true);
const autoInvoice = ref(false);

const presetAvatars = [
  "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=150",
  "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=150",
  "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&q=80&w=150",
  "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&q=80&w=150"
];

onMounted(async () => {
  await userStore.loadUser();
  if (userStore.user) {
    nickname.value = userStore.user.nickname;
    phone.value = userStore.user.phone;
    email.value = userStore.user.email;
    avatar.value = userStore.user.avatar;
  }
});

const handleSaveProfile = async () => {
  if (!nickname.value) {
    alert("昵称不能为空哦！");
    return;
  }

  const payload = {
    ...userStore.user,
    nickname: nickname.value,
    phone: phone.value,
    email: email.value,
    avatar: avatar.value
  };

  try {
    await userStore.updateProfile(payload);
    alert("个人账号配置更新成功！");
    router.push("/profile");
  } catch (err) {
    alert("保存失败: " + err.message);
  }
};
</script>

<template>
  <FrontendLayout>
    <div class="max-w-[800px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
      
      <!-- Back Header -->
      <div class="flex items-center gap-3 mb-8">
        <button 
          @click="router.push('/profile')" 
          class="p-2 bg-white border border-[#e4e2de] hover:bg-[#efeeea] rounded-xl text-stone-600 transition-colors cursor-pointer"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-xl font-bold tracking-tight text-stone-800 font-display flex items-center gap-2">
            <Settings class="w-5 h-5 text-stone-600" />
            <span>个人账号设置</span>
          </h1>
          <p class="text-xs text-stone-500 mt-0.5">
            修改和管理您的公开昵称、联系方式、登录头像及安全通知偏好。
          </p>
        </div>
      </div>

      <!-- Settings Card -->
      <div v-if="userStore.user" class="bg-white border border-[#e4e2de] rounded-3xl p-6 shadow-sm space-y-8">
        
        <!-- Avatar selection -->
        <div>
          <h4 class="text-xs font-bold text-stone-500 uppercase tracking-widest font-mono mb-4">修改头像</h4>
          <div class="flex flex-col sm:flex-row items-center gap-6">
            <img 
              :src="avatar" 
              alt="Current Avatar" 
              class="w-20 h-20 rounded-full border-2 border-[#ff6b35] object-cover shadow-sm"
            />
            <div>
              <p class="text-xs text-stone-500 mb-2.5 font-medium">从精选趣味头像中选择：</p>
              <div class="flex gap-3">
                <button
                  v-for="(av, idx) in presetAvatars"
                  :key="idx"
                  @click="avatar = av"
                  :class="`w-11 h-11 rounded-full overflow-hidden border-2 transition-all cursor-pointer ${
                    avatar === av ? 'border-[#ff6b35] ring-2 ring-[#ffdbcc]' : 'border-[#e4e2de] hover:border-stone-400'
                  }`"
                >
                  <img :src="av" class="w-full h-full object-cover" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="border-t border-stone-100 my-6"></div>

        <!-- Base Info Form -->
        <div class="space-y-4">
          <h4 class="text-xs font-bold text-stone-500 uppercase tracking-widest font-mono mb-2">基础信息</h4>
          
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <!-- Username read-only -->
            <div>
              <label class="block text-[11px] font-bold text-stone-400 mb-1.5 uppercase">登录账号 (不可修改)</label>
              <input
                type="text"
                :value="userStore.user.username"
                disabled
                class="w-full text-xs px-3.5 py-2.5 rounded-xl bg-stone-50 border border-stone-200 text-stone-400 cursor-not-allowed font-mono"
              />
            </div>

            <!-- Nickname -->
            <div>
              <label class="block text-[11px] font-bold text-stone-600 mb-1.5 uppercase">我的昵称 *</label>
              <div class="relative">
                <input
                  type="text"
                  v-model="nickname"
                  placeholder="展示昵称"
                  class="w-full text-xs pl-10 pr-3.5 py-2.5 rounded-xl bg-white border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]"
                />
                <User class="w-4 h-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              </div>
            </div>

            <!-- Phone -->
            <div>
              <label class="block text-[11px] font-bold text-stone-600 mb-1.5 uppercase">联系电话 *</label>
              <div class="relative">
                <input
                  type="text"
                  v-model="phone"
                  placeholder="请输入手机号"
                  class="w-full text-xs pl-10 pr-3.5 py-2.5 rounded-xl bg-white border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]"
                />
                <Phone class="w-4 h-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              </div>
            </div>

            <!-- Email -->
            <div>
              <label class="block text-[11px] font-bold text-stone-600 mb-1.5 uppercase">邮箱地址 *</label>
              <div class="relative">
                <input
                  type="email"
                  v-model="email"
                  placeholder="请输入邮箱"
                  class="w-full text-xs pl-10 pr-3.5 py-2.5 rounded-xl bg-white border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]"
                />
                <Mail class="w-4 h-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              </div>
            </div>
          </div>
        </div>

        <div class="border-t border-stone-100 my-6"></div>

        <!-- Preferences -->
        <div class="space-y-4">
          <h4 class="text-xs font-bold text-stone-500 uppercase tracking-widest font-mono mb-2">通知与服务偏好</h4>
          
          <div class="space-y-3 text-xs text-stone-650">
            <div class="flex items-center justify-between p-3.5 bg-[#fbf9f5] border border-stone-150 rounded-2xl">
              <div>
                <h5 class="font-bold text-stone-850">接收促销与上新提醒</h5>
                <p class="text-[10px] text-stone-400 mt-0.5">每周推送麦乐迪新品研发、神券领取及满赠大促提醒。</p>
              </div>
              <input type="checkbox" v-model="acceptPromo" class="w-4 h-4 accent-[#ff6b35]" />
            </div>

            <div class="flex items-center justify-between p-3.5 bg-[#fbf9f5] border border-stone-150 rounded-2xl">
              <div>
                <h5 class="font-bold text-stone-850">自动生成电子发票</h5>
                <p class="text-[10px] text-stone-400 mt-0.5">勾选后，订单在确认收货完成后将自动推送发票至您的邮箱中。</p>
              </div>
              <input type="checkbox" v-model="autoInvoice" class="w-4 h-4 accent-[#ff6b35]" />
            </div>
          </div>
        </div>

        <!-- Submit Buttons -->
        <div class="pt-4 border-t border-stone-100 flex justify-end gap-3">
          <button
            type="button"
            @click="router.push('/profile')"
            class="px-5 py-2.5 border border-[#e4e2de] text-stone-500 font-bold rounded-xl text-xs hover:bg-[#fbf9f5] transition-colors cursor-pointer"
          >
            返回取消
          </button>
          
          <button
            type="button"
            @click="handleSaveProfile"
            class="px-5 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold rounded-xl text-xs flex items-center gap-1.5 cursor-pointer shadow-sm active:scale-95 transition-all"
          >
            <Save class="w-4 h-4" />
            <span>保存个性设置</span>
          </button>
        </div>

      </div>
    </div>
  </FrontendLayout>
</template>
