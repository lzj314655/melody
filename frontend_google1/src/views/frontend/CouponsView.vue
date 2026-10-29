<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import { couponFace } from "@/utils/coupon";
import { fetchAvailableCoupons, fetchAllCouponsApi } from "@/api/user";
import FrontendLayout from "@/components/FrontendLayout.vue";
import { ArrowLeft, Ticket, Gift, CheckCircle } from "lucide-vue-next";

const router = useRouter();
const userStore = useUserStore();

const availableList = ref([]);
// 终身一次：couponId -> 状态（可用/已用/过期）。已用的显示"已使用"，其余显示"已领取"
const claimedStatus = ref(new Map());
const showToast = ref(false);
const toastMessage = ref("");

const formatDate = (s) => {
  if (!s) return "";
  try {
    const d = Array.isArray(s)
      ? new Date(s[0], s[1] - 1, s[2] || 1)
      : new Date(s);
    if (isNaN(d.getTime())) return String(s);
    return `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, "0")}-${d.getDate().toString().padStart(2, "0")}`;
  } catch { return String(s); }
};

async function load() {
  await userStore.loadCoupons().catch(() => {});
  try {
    const list = await fetchAvailableCoupons();
    availableList.value = (list || []).map(c => ({
      ...c,
      title: c.name,
      expiryDate: formatDate(c.endDate),
      description: c.description,
    }));
    // 终身一次：只要领过（含已用/过期）都算"已领取"，不可再领
    try {
      const all = await fetchAllCouponsApi();
      claimedStatus.value = new Map((all || []).map(uc => [uc.couponId, uc.status || "可用"]));
    } catch {
      // 兜底：退化为仅看"可用"状态
      claimedStatus.value = new Map(
        (userStore.coupons || [])
          .filter(c => c.status === "可用" || !c.status)
          .map(c => [c.couponId || c.id, c.status || "可用"])
      );
    }
  } catch (e) {
    availableList.value = [];
  }
}

onMounted(load);

// 是否已领过（任何状态）→ 按钮置灰
const isClaimed = (couponId) => claimedStatus.value.has(couponId);
// 领过且已核销 → 按钮文案显示"已使用"
const isUsed = (couponId) => claimedStatus.value.get(couponId) === "已用";

const handleClaimCoupon = async (coupon) => {
  // 未登录先跳登录页
  const userToken = localStorage.getItem("access_token");
  if (!userToken) {
    router.push("/login");
    return;
  }
  try {
    await userStore.claimCoupon(coupon.id);
    claimedStatus.value.set(coupon.id, "可用"); // 立刻标记为已领取
    toastMessage.value = `成功领到一张「${coupon.name || coupon.title}」！`;
    showToast.value = true;
    setTimeout(() => (showToast.value = false), 2500);
  } catch (err) {
    toastMessage.value = err.code === 1002 || err.code === 401 || err.code === 403
      ? "请先登录后再领取"
      : (err.message || "领取失败，请稍后再试");
    showToast.value = true;
    setTimeout(() => (showToast.value = false), 2500);
  }
};
</script>

<template>
  <FrontendLayout>
    <Transition name="fade">
      <div v-if="showToast" class="fixed top-20 right-8 z-50 bg-[#004e64] text-white px-5 py-3.5 rounded-xl shadow-lg border border-[#e0f2f1]/10 flex items-center gap-2 text-xs">
        <span class="material-symbols-outlined text-green-400 text-lg">check_circle</span>
        <span>{{ toastMessage }}</span>
      </div>
    </Transition>

    <div class="max-w-[1080px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="flex items-center gap-3 mb-8">
        <button @click="router.push('/profile')" class="p-2 bg-white border border-[#e4e2de] hover:bg-[#efeeea] rounded-xl text-stone-600 transition-colors cursor-pointer">
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-xl font-bold tracking-tight text-stone-800 font-display flex items-center gap-2">
            <Ticket class="w-5 h-5 text-[#ff6b35]" /><span>领券中心</span>
          </h1>
          <p class="text-xs text-stone-500 mt-0.5">抢专享无门槛或满减特惠神券，享受绝佳优惠！</p>
        </div>
      </div>

      <div class="bg-gradient-to-r from-[#ffe1d4] to-[#ffebdf] rounded-3xl p-6 border border-[#ff6b35]/15 flex flex-col md:flex-row items-center justify-between gap-6 mb-8">
        <div class="flex items-center gap-4">
          <div class="w-12 h-12 rounded-2xl bg-[#ff6b35] text-white flex items-center justify-center shadow-md animate-bounce">
            <Gift class="w-6 h-6" />
          </div>
          <div>
            <h3 class="text-sm font-bold text-stone-800">新手好礼进行中</h3>
            <p class="text-[11px] text-stone-600 mt-1 leading-normal">
              金牌会员专享折扣，首购领满减福利！每人每天限领 1 张，有效期内下单立减。
            </p>
          </div>
        </div>
        <div class="flex gap-4 text-xs font-mono shrink-0">
          <div class="bg-white/80 backdrop-blur border border-stone-200/40 p-3 rounded-2xl text-center shadow-sm">
            <span class="block text-stone-400 text-[9px] font-semibold">今日已发</span>
            <span class="text-sm font-black text-stone-800">{{ availableList.length * 1000 + 821 }}</span>
          </div>
          <div class="bg-white/80 backdrop-blur border border-stone-200/40 p-3 rounded-2xl text-center shadow-sm">
            <span class="block text-stone-400 text-[9px] font-semibold">好评福利率</span>
            <span class="text-sm font-black text-[#ff6b35]">99.8%</span>
          </div>
        </div>
      </div>

      <div v-if="availableList.length === 0" class="bg-white border border-[#e4e2de] rounded-3xl p-16 text-center shadow-sm">
        <Ticket class="w-12 h-12 text-stone-300 mx-auto mb-3" />
        <h3 class="text-sm font-bold text-stone-700">目前暂无正在进行的领券活动</h3>
        <p class="text-xs text-stone-400 mt-1">关注麦乐迪公众号或社区活动，超值特惠不容错过</p>
      </div>

      <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div v-for="coupon in availableList" :key="coupon.id" class="border border-[#e4e2de] rounded-3xl overflow-hidden hover:shadow-md transition-shadow bg-white flex">
          <div class="bg-gradient-to-br from-[#ff6b35] to-[#ffdbcc] text-white p-6 flex flex-col justify-center items-center shrink-0 min-w-[120px] relative">
            <div class="absolute -top-3 -left-3 w-6 h-6 rounded-full bg-[#fbf9f5]"></div>
            <div class="absolute -bottom-3 -left-3 w-6 h-6 rounded-full bg-[#fbf9f5]"></div>
            <span class="text-3xl font-black font-display leading-none">
              <template v-for="(cf, i) in [couponFace(coupon.type, coupon.discount)]" :key="i">
                {{ cf.prefix }}{{ cf.value }}<span class="text-base">{{ cf.suffix }}</span>
              </template>
            </span>
            <span class="text-[10px] font-bold mt-1 opacity-90 uppercase tracking-widest">满￥{{ coupon.minSpend }}</span>
            <div class="w-full border-t border-dashed border-white/20 my-2.5"></div>
            <span class="text-[9px] font-mono tracking-wider bg-black/10 px-2 py-0.5 rounded-full">
              {{ coupon.type || '特惠券' }}
            </span>
          </div>

          <div class="p-5 flex-1 flex flex-col justify-between relative">
            <div class="absolute -top-3 -right-3 w-6 h-6 rounded-full bg-[#fbf9f5]"></div>
            <div class="absolute -bottom-3 -right-3 w-6 h-6 rounded-full bg-[#fbf9f5]"></div>
            <div>
              <h5 class="text-xs font-bold text-stone-800 leading-snug line-clamp-2">{{ coupon.title || coupon.name }}</h5>
              <p class="text-[10px] text-stone-400 mt-1.5 leading-normal">{{ coupon.description }}</p>
            </div>
            <div class="flex items-center justify-between border-t border-stone-100 pt-3 mt-4 text-[10px] text-stone-400">
              <span class="font-mono">有效期至: {{ coupon.expiryDate }}</span>
              <button
                @click="handleClaimCoupon(coupon)"
                :disabled="isClaimed(coupon.id)"
                :class="`px-3 py-1.5 text-[10px] font-bold rounded-xl transition-all cursor-pointer ${
                  isClaimed(coupon.id)
                    ? 'bg-stone-100 text-stone-400 cursor-not-allowed'
                    : 'bg-[#ff6b35] text-white hover:bg-[#ff6b35]/90 shadow-sm hover:scale-105 active:scale-95'
                }`"
              >
                <span v-if="isClaimed(coupon.id)" class="flex items-center gap-0.5">
                  <CheckCircle class="w-3 h-3 text-stone-400" /><span>{{ isUsed(coupon.id) ? '已使用' : '已领取' }}</span>
                </span>
                <span v-else>立即领取</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </FrontendLayout>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease, transform 0.3s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(-10px); }
</style>
