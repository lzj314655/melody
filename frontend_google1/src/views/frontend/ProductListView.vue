<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useRecommendStore } from "@/store/recommend";
import { useCartStore } from "@/store/cart";
import { useUserStore } from "@/store/user";
import { couponFace } from "@/utils/coupon";
import { reportBehavior } from "@/api/behavior";
import { fetchAllCouponsApi } from "@/api/user";
import FrontendLayout from "@/components/FrontendLayout.vue";
import { Search, Flame, ArrowUpDown, ChevronRight, Tag, Sparkles, Megaphone } from "lucide-vue-next";

const router = useRouter();
const recommendStore = useRecommendStore();
const cartStore = useCartStore();
const userStore = useUserStore();

const searchKeyword = ref("");
const selectedCategory = ref("全部");
const sortBy = ref("default"); // default, price-asc, price-desc, sales-desc, rating-desc

// F-R-005 / F-R-004 首页"为你推荐"
const personalRecs = ref([]);
const personalRecTitle = ref("热门推荐");
onMounted(async () => {
  await recommendStore.loadCategories();
  await recommendStore.loadAnnouncements();
  await recommendStore.loadRecommendations();
  if (userStore.isLoggedIn) {
    await userStore.loadUser();
    await userStore.loadCoupons();          // 拉已领列表，标记按钮状态
    await loadClaimedCouponIds();           // 终身一次：拉全部状态（含已用/过期）判断是否已领
    await userStore.loadAvailableCoupons(); // hero 区可领取列表
    await cartStore.loadCart().catch(() => {});
    // 登录用户走 ItemCF + Redis 个性化推荐
    personalRecTitle.value = "为你推荐";
    personalRecs.value = (await recommendStore.loadPersonalRecommend(8)) || [];
  } else {
    await userStore.loadAvailableCoupons();
    // 游客走冷启动
    personalRecs.value = (await recommendStore.loadColdStart(8)) || [];
  }
});

const filteredProducts = computed(() => {
  let list = recommendStore.featuredProducts;

  // Search filter
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.toLowerCase();
    list = list.filter(p => p.name.toLowerCase().includes(kw) || p.description.toLowerCase().includes(kw));
  }

  // Category filter
  if (selectedCategory.value !== "全部") {
    list = list.filter(p => p.category === selectedCategory.value);
  }

  // Sorting
  const sorted = [...list];
  if (sortBy.value === "price-asc") {
    sorted.sort((a, b) => a.price - b.price);
  } else if (sortBy.value === "price-desc") {
    sorted.sort((a, b) => b.price - a.price);
  } else if (sortBy.value === "sales-desc") {
    sorted.sort((a, b) => b.sales - a.sales);
  } else if (sortBy.value === "rating-desc") {
    sorted.sort((a, b) => b.rating - a.rating);
  }
  return sorted;
});

// F-R-001: 列表页不再对展示商品批量上报 VIEW。
// 原实现 watch(filteredProducts) 会对列表里每个商品都发一条 source=list 的 VIEW，把"曝光"
// 误标成"浏览"——同一批时间戳最新，会淹没真实浏览历史，让 /profile 的"近期浏览"返回用户
// 根本没点开过的商品。真实浏览由 ProductDetailView 上报 source=detail 的 VIEW，这里不再染指。

const quickAddToCart = async (product) => {
  if (!userStore.isLoggedIn) {
    toastMessage.value = "请先登录后再加入购物车";
    showToast.value = true;
    setTimeout(() => (showToast.value = false), 2000);
    router.push("/login");
    return;
  }
  // 保证产品带 skuId：详情页才会拿，这里用一个 mock fallback 不阻塞 UI：跳详情页
  if (!product.skuId) {
    router.push(`/product/${product.id}`);
    return;
  }
  const defaultSpec = product.specs[0] || "150g";
  reportBehavior("ADD_CART", product.id, { spec: defaultSpec, quantity: 1, source: "list_quick" });
  try {
    await cartStore.addToCart(product, defaultSpec, 1);
    toastMessage.value = `已将 1 件「${product.name}」加入购物车！`;
  } catch (e) {
    toastMessage.value = `加车失败：${e.message}`;
  }
  showToast.value = true;
  setTimeout(() => (showToast.value = false), 2500);
};

// Simple visual feedback toast
const showToast = ref(false);
const toastMessage = ref("");

// F-A-063 公告:显示置顶优先的第一条
const topAnnouncement = computed(() => (recommendStore.announcements || [])[0] || null);

// 终身一次：已领取过的 couponId 集合（含已用/过期），全量从 /coupon/mine?status=ALL 拉取
const claimedCouponIds = ref(new Set());
const loadClaimedCouponIds = async () => {
  try {
    const all = await fetchAllCouponsApi();
    claimedCouponIds.value = new Set((all || []).map(uc => uc.couponId));
  } catch {
    claimedCouponIds.value = new Set();
  }
};

// 判断该 couponId 是否已领取：只要领过（可用/已用/过期）都算，不可再领
const isClaimed = (couponId) => claimedCouponIds.value.has(couponId);

const handleClaimCoupon = async (coupon) => {
  if (!userStore.isLoggedIn) {
    toastMessage.value = "请先登录后再领取";
    showToast.value = true;
    setTimeout(() => (showToast.value = false), 2000);
    router.push("/login");
    return;
  }
  try {
    await userStore.claimCoupon(coupon.id);
    claimedCouponIds.value.add(coupon.id); // 立刻标记已领取，避免按钮复位
    await userStore.loadAvailableCoupons();
    toastMessage.value = `成功领取「${coupon.name || coupon.title}」`;
    showToast.value = true;
    setTimeout(() => (showToast.value = false), 2500);
  } catch (e) {
    toastMessage.value = `领取失败：${e.message}`;
    showToast.value = true;
    setTimeout(() => (showToast.value = false), 2500);
  }
};
</script>

<template>
  <FrontendLayout>
    <!-- Simple Toast -->
    <Transition name="fade">
      <div 
        v-if="showToast" 
        class="fixed top-20 right-8 z-50 bg-[#004e64] text-white px-5 py-3.5 rounded-xl shadow-lg border border-[#e0f2f1]/10 flex items-center gap-2 text-xs"
      >
        <span class="material-symbols-outlined text-green-400 text-lg">check_circle</span>
        <span>{{ toastMessage }}</span>
      </div>
    </Transition>

    <!-- F-U-010 首页轮播图 + F-A-063 公告条 + Hero Banner 合成一整块 -->
    <section class="hero-section relative overflow-hidden bg-gradient-to-r from-[#ffebe3] via-[#ffdbcc] to-[#ffebe3] border-b border-[#ff6b35]/10">
      <!-- 共享装饰 -->
      <div class="hero-blob hero-blob-1 pointer-events-none absolute -top-16 -left-20 w-72 h-72 rounded-full bg-white/45 blur-2xl"></div>
      <div class="hero-blob hero-blob-2 pointer-events-none absolute -bottom-24 right-1/3 w-80 h-80 rounded-full bg-[#ff6b35]/15 blur-3xl"></div>
      <div class="hero-blob hero-blob-3 pointer-events-none absolute top-1/3 right-16 w-28 h-28 rounded-full bg-white/30 blur-xl"></div>
      <div class="hero-blob hero-blob-4 pointer-events-none absolute bottom-10 left-1/3 w-20 h-20 rounded-full bg-white/25 blur-lg"></div>
      <!-- 共享流光 -->
      <div class="hero-shine pointer-events-none absolute inset-y-0 -left-1/2 w-1/2"></div>

      <!-- F-A-063 公告条 -->
      <div
        v-if="topAnnouncement"
        class="announcement-bar relative w-full border-b border-[#ff6b35]/15"
      >
        <div class="relative flex items-center gap-4 px-4 sm:px-6 lg:px-8 py-3 max-w-[1280px] mx-auto">
          <div class="flex items-center gap-2 shrink-0">
            <div class="announcement-icon w-8 h-8 rounded-xl bg-[#ff6b35] flex items-center justify-center shadow-sm">
              <Megaphone class="w-4 h-4 text-white" />
            </div>
            <span class="text-[11px] font-bold tracking-wider uppercase text-[#ff6b35] whitespace-nowrap">
              平台公告
            </span>
          </div>
          <div class="flex-1 min-w-0">
            <h4 class="text-sm font-bold text-stone-800 leading-snug truncate">
              {{ topAnnouncement.title }}
            </h4>
            <p class="text-xs text-stone-500 leading-relaxed line-clamp-1 mt-0.5">
              {{ topAnnouncement.content }}
            </p>
          </div>
          <span class="text-[10px] text-stone-400 font-mono shrink-0 whitespace-nowrap">
            {{ topAnnouncement.publishAt }}
          </span>
        </div>
      </div>

      <!-- Hero Banner with active campaigns -->
      <div class="hero-banner relative shrink-0">
        <div class="relative max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-10 grid grid-cols-1 md:grid-cols-12 gap-8 items-center">
          <!-- left promotion slogan -->
          <div class="hero-left md:col-span-7 flex flex-col justify-center">
            <div class="hero-tag inline-flex items-center gap-1.5 px-3 py-1 bg-[#ff6b35] text-white text-[11px] font-bold rounded-full w-fit mb-4 shadow-sm shadow-[#ff6b35]/30">
              <Flame class="w-3.5 h-3.5 fill-current animate-bounce" />
              <span>首发大特惠</span>
            </div>
            <h1 class="hero-title text-3xl sm:text-4xl lg:text-5xl font-black text-stone-900 tracking-tight font-display mb-3 leading-tight">
              尝一口，爱上的<br/><span class="text-[#ff6b35]">“麦乐迪”美味</span>
            </h1>
            <p class="hero-desc text-sm text-stone-600 leading-relaxed max-w-xl mb-6">
              物理烘焙自然红松子，秘制炭烤肉脯，经典手打牛轧糖。精选原产地健康食材，给你无负担的味蕾享受，每周上新不停歇。
            </p>

            <!-- quick stats widgets -->
            <div class="hero-stats grid grid-cols-3 gap-4 max-w-md pt-2 border-t border-stone-200">
              <div class="hero-stat">
                <span class="block text-xl font-bold font-display text-[#ff6b35]">100%</span>
                <span class="text-[11px] text-stone-500">原产地严选</span>
              </div>
              <div class="hero-stat">
                <span class="block text-xl font-bold font-display text-stone-800">48h</span>
                <span class="text-[11px] text-stone-500">极速保鲜达</span>
              </div>
              <div class="hero-stat">
                <span class="block text-xl font-bold font-display text-stone-800">1.2w+</span>
                <span class="text-[11px] text-stone-500">金牌好评率</span>
              </div>
            </div>
          </div>

          <!-- right promo voucher claim cards -->
          <div class="hero-right md:col-span-5 flex flex-col gap-4">
            <div class="hero-coupon-panel bg-white/80 backdrop-blur-md rounded-2xl border border-[#e4e2de] p-5 shadow-sm">
              <h4 class="text-xs font-bold text-stone-700 tracking-wider uppercase font-mono mb-3 flex items-center gap-1">
                <Tag class="w-3.5 h-3.5 text-[#ff6b35]" /> 可领取专享优惠券
              </h4>

              <div class="space-y-2.5">
                <div
                  v-for="coupon in userStore.availableCoupons"
                  :key="coupon.id"
                  class="hero-coupon-card flex items-center justify-between bg-white border border-[#e4e2de] rounded-xl p-3 hover:border-[#ff6b35]/40 hover:-translate-y-0.5 hover:shadow-md transition-all"
                >
                  <div class="flex items-center gap-3">
                    <div class="bg-[#ffdbcc] text-[#ff6b35] font-black text-lg p-2 rounded-lg leading-none font-display">
                      <template v-for="(cf, i) in [couponFace(coupon.type, coupon.discount)]" :key="i">
                        {{ cf.prefix }}{{ cf.value }}{{ cf.suffix }}
                      </template>
                    </div>
                    <div>
                      <h5 class="text-xs font-bold text-stone-800 leading-tight">{{ coupon.name }}</h5>
                      <p class="text-[10px] text-stone-500 mt-0.5">满 ￥{{ coupon.minSpend }} 可用</p>
                    </div>
                  </div>
                  <button
                    @click="handleClaimCoupon(coupon)"
                    :disabled="isClaimed(coupon.id)"
                    :class="`text-[11px] font-bold px-3 py-1.5 rounded-lg border transition-all ${
                      isClaimed(coupon.id)
                        ? 'bg-stone-100 text-stone-400 border-stone-200 cursor-not-allowed'
                        : 'text-[#ff6b35] hover:bg-[#ffdbcc]/40 border-[#ff6b35]/20 cursor-pointer'
                    }`"
                  >
                    {{ isClaimed(coupon.id) ? '已领取' : '领取' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- F-R-005 / F-R-004 首页"为你推荐" / "热门推荐"——基于 ItemCF + Redis（登录用户）或 coldStart（游客） -->
    <div
      v-if="personalRecs.length > 0"
      class="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 pt-6"
    >
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-5 shadow-sm">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-xl bg-gradient-to-br from-[#ffdbcc] to-[#ffebe3] flex items-center justify-center">
              <Sparkles class="w-4 h-4 text-[#ff6b35]" />
            </div>
            <div>
              <h2 class="text-sm font-bold text-stone-800 font-display flex items-center gap-1.5">
                {{ personalRecTitle }}
                <span class="text-[10px] font-mono font-normal text-[#ff6b35] bg-[#ffebe3] border border-[#ffdbcc] px-1.5 py-0.5 rounded-md">
                  {{ userStore.isLoggedIn ? 'ItemCF · Redis' : 'coldStart' }}
                </span>
              </h2>
              <p class="text-[11px] text-stone-400 mt-0.5">
                {{ userStore.isLoggedIn ? '基于你近 30 天的浏览 / 加购 / 收藏行为个性化计算' : '当前热销零食 Top 8，先随便逛逛吧～' }}
              </p>
            </div>
          </div>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
          <div
            v-for="(p, idx) in personalRecs"
            :key="p.id"
            class="cursor-pointer group flex flex-col gap-2 p-2.5 rounded-xl hover:bg-[#fbf9f5] transition-colors"
            @click="router.push(`/product/${p.id}`)"
          >
            <div class="relative w-full aspect-square rounded-lg overflow-hidden bg-[#fbf9f5] border border-stone-100">
              <img
                v-if="p.image"
                :src="p.image"
                :alt="p.name"
                class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                loading="lazy"
              />
              <div v-else class="w-full h-full flex items-center justify-center text-stone-300 text-xs font-mono">
                #{{ p.id }}
              </div>
              <span
                v-if="idx === 0 && userStore.isLoggedIn"
                class="recommendation-glow-active absolute top-1 left-1 text-[9px] font-bold bg-[#ff6b35] text-white px-1.5 py-0.5 rounded-md"
              >
                最匹配
              </span>
            </div>
            <div class="min-w-0">
              <h3 class="text-xs font-bold text-stone-800 truncate leading-snug group-hover:text-[#ff6b35] transition-colors">
                {{ p.name }}
              </h3>
              <div class="flex items-baseline gap-1 mt-1">
                <span class="text-sm font-black text-[#ff6b35] font-display">￥{{ p.price.toFixed(2) }}</span>
                <span v-if="p.sales" class="text-[10px] text-stone-400 font-mono">已售 {{ p.sales }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Category selector & filters -->
    <div class="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
      
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-8">
        <!-- Categories tabs -->
        <div class="flex flex-wrap gap-1.5 bg-[#efeeea] p-1 rounded-2xl w-full md:w-auto">
          <button
            v-for="cat in recommendStore.categories"
            :key="cat"
            @click="selectedCategory = cat"
            :class="`px-4 py-2 text-xs font-semibold rounded-xl transition-all duration-200 cursor-pointer ${
              selectedCategory === cat
                ? 'bg-white text-stone-900 shadow-sm'
                : 'text-[#5a4136] hover:text-[#1b1c1a] hover:bg-white/30'
            }`"
          >
            {{ cat }}
          </button>
        </div>

        <!-- search bar and sorting menu -->
        <div class="flex items-center gap-2.5 w-full md:w-auto shrink-0">
          <!-- Search input -->
          <div class="relative flex-1 sm:w-64">
            <input
              type="text"
              v-model="searchKeyword"
              placeholder="搜索美味零食..."
              class="w-full text-xs pl-8 pr-3 py-2.5 rounded-xl bg-white border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] transition-colors shadow-sm"
            />
            <Search class="w-4 h-4 text-stone-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
          </div>

          <!-- Sort dropdown -->
          <div class="relative shrink-0">
            <select
              v-model="sortBy"
              class="appearance-none text-xs pl-8 pr-8 py-2.5 rounded-xl bg-white border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] cursor-pointer shadow-sm text-stone-700"
            >
              <option value="default">默认推荐排序</option>
              <option value="price-asc">价格：从低到高</option>
              <option value="price-desc">价格：从高到低</option>
              <option value="sales-desc">热度：按销量</option>
              <option value="rating-desc">口碑：按好评</option>
            </select>
            <ArrowUpDown class="w-3.5 h-3.5 text-stone-400 absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>
        </div>
      </div>

      <!-- Products Grid -->
      <div v-if="recommendStore.loading" class="flex flex-col items-center justify-center py-20">
        <span class="material-symbols-outlined text-stone-300 text-5xl animate-spin">progress_activity</span>
        <p class="text-sm text-stone-500 mt-2">美味制作中，请稍候...</p>
      </div>

      <div v-else-if="filteredProducts.length === 0" class="flex flex-col items-center justify-center py-20 bg-white border border-[#e4e2de] rounded-3xl">
        <span class="material-symbols-outlined text-stone-300 text-6xl">cookie_off</span>
        <h4 class="text-base font-bold text-stone-700 mt-3">没有找到匹配的零食</h4>
        <p class="text-xs text-stone-400 mt-1">换个分类或检索词再试试吧~</p>
      </div>

      <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        <!-- Snack Product Card -->
        <div
          v-for="product in filteredProducts"
          :key="product.id"
          class="group bg-white border border-[#e4e2de] rounded-2xl overflow-hidden hover:shadow-md hover:border-[#ff6b35]/20 transition-all duration-300 flex flex-col justify-between"
        >
          <!-- image wrapper with link to detail -->
          <div 
            @click="router.push(`/product/${product.id}`)"
            class="relative aspect-[4/3] w-full overflow-hidden bg-stone-100 cursor-pointer"
          >
            <img
              :src="product.image"
              :alt="product.name"
              class="object-cover w-full h-full transform transition-transform duration-500 group-hover:scale-105"
            />
            
            <!-- Quick tag badge -->
            <div class="absolute top-3 left-3 flex flex-wrap gap-1">
              <span 
                v-for="(tag, idx) in product.tags" 
                :key="idx"
                class="px-2 py-0.5 text-[9px] font-bold text-white bg-black/60 rounded-md backdrop-blur-sm"
              >
                {{ tag }}
              </span>
            </div>

            <!-- rating badge -->
            <div class="absolute bottom-3 right-3 bg-white/95 backdrop-blur-sm px-2 py-0.5 rounded-lg text-[9px] font-bold text-stone-800 flex items-center gap-0.5 shadow-sm">
              <span class="material-symbols-outlined text-[11px] text-[#ff6b35] fill-current">star</span>
              <span>{{ product.rating }}</span>
            </div>
          </div>

          <!-- Product info -->
          <div class="p-5 flex-1 flex flex-col justify-between">
            <div>
              <div class="flex items-center justify-between gap-1 mb-1.5">
                <span class="text-[10px] text-stone-400 font-bold uppercase font-mono tracking-wider">
                  {{ product.category }}
                </span>
                <span class="text-[10px] text-stone-500 font-medium">
                  已售 {{ product.sales }} 件
                </span>
              </div>
              <h3 
                @click="router.push(`/product/${product.id}`)"
                class="text-sm font-bold text-stone-800 hover:text-[#ff6b35] transition-colors leading-snug cursor-pointer line-clamp-1"
              >
                {{ product.name }}
              </h3>
              <p class="text-xs text-stone-500 mt-1 line-clamp-2 leading-relaxed">
                {{ product.description }}
              </p>
            </div>

            <div class="mt-4 flex items-center justify-between border-t border-stone-100 pt-3">
              <!-- price -->
              <div class="flex items-baseline gap-1.5">
                <span class="text-[#ff6b35] text-base font-black font-display">
                  ￥{{ product.price.toFixed(1) }}
                </span>
                <span class="text-stone-400 text-xs line-through">
                  ￥{{ product.marketPrice.toFixed(1) }}
                </span>
              </div>

              <!-- Cart Add Button -->
              <button
                @click="quickAddToCart(product)"
                class="w-8 h-8 rounded-full bg-[#ff6b35] text-white hover:bg-[#ff6b35]/90 flex items-center justify-center transform active:scale-90 transition-all cursor-pointer"
                title="加入购物车"
              >
                <span class="material-symbols-outlined text-sm leading-none">add_shopping_cart</span>
              </button>
            </div>
          </div>
        </div>
      </div>

    </div>
  </FrontendLayout>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* ============ 整块动态效果（公告条 + Hero Banner 共享） ============ */

/* 统一的基础 fade —— 上下子元素都用 */
@keyframes hero-fade-up {
  from { opacity: 0; transform: translateY(16px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* 整块淡入 */
.hero-section {
  animation: hero-fade-up 0.6s ease-out both;
}

/* 公告条从顶部滑入（与整块方向一致：稍早于 hero banner） */
.announcement-bar {
  animation: hero-fade-up 0.5s ease-out 0.05s both;
}

/* Hero Banner 整体略晚于公告条 */
.hero-banner {
  animation: hero-fade-up 0.6s ease-out 0.15s both;
}

/* 整块共享 —— 装饰球漂浮（4 颗，节奏各异） */
@keyframes hero-blob-float-1 {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50%      { transform: translate3d(20px, -18px, 0) scale(1.05); }
}
@keyframes hero-blob-float-2 {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50%      { transform: translate3d(-24px, 16px, 0) scale(1.08); }
}
@keyframes hero-blob-float-3 {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); opacity: 0.6; }
  50%      { transform: translate3d(10px, 12px, 0) scale(1.1); opacity: 0.9; }
}
@keyframes hero-blob-float-4 {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50%      { transform: translate3d(-14px, -10px, 0) scale(1.12); }
}

.hero-blob-1 { animation: hero-blob-float-1  9s ease-in-out infinite; }
.hero-blob-2 { animation: hero-blob-float-2 11s ease-in-out infinite; }
.hero-blob-3 { animation: hero-blob-float-3  7s ease-in-out infinite; }
.hero-blob-4 { animation: hero-blob-float-4  8s ease-in-out infinite; }

/* 整块共享 —— 流光扫过（一条） */
@keyframes hero-shine-sweep {
  0% {
    transform: translateX(0);
  }
  60%,
  100% {
    transform: translateX(380%);
  }
}

.hero-shine {
  background: linear-gradient(
    100deg,
    transparent 0%,
    transparent 35%,
    rgba(255, 255, 255, 0.55) 50%,
    transparent 65%,
    transparent 100%
  );
  animation: hero-shine-sweep 6s ease-in-out infinite;
}

/* 平台公告 —— 喇叭图标徽标脉动 + 喇叭左右晃动 */
@keyframes hero-badge-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 107, 53, 0.45); }
  50%      { box-shadow: 0 0 0 8px rgba(255, 107, 53, 0); }
}

.announcement-icon {
  animation: hero-badge-pulse 2s ease-out infinite;
}

@keyframes hero-megaphone-shake {
  0%, 100% { transform: rotate(0deg); }
  20%      { transform: rotate(-10deg); }
  40%      { transform: rotate(8deg); }
  60%      { transform: rotate(-6deg); }
  80%      { transform: rotate(4deg); }
}

.announcement-icon svg {
  animation: hero-megaphone-shake 1.8s ease-in-out infinite;
  transform-origin: center;
}

/* Hero Banner 左右内容滑入 */
@keyframes hero-slide-from-left {
  from { opacity: 0; transform: translateX(-40px); }
  to   { opacity: 1; transform: translateX(0); }
}
@keyframes hero-slide-from-right {
  from { opacity: 0; transform: translateX(40px); }
  to   { opacity: 1; transform: translateX(0); }
}

.hero-left {
  animation: hero-slide-from-left 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) 0.3s both;
}
.hero-right {
  animation: hero-slide-from-right 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) 0.4s both;
}

/* 左侧内部元素阶梯动画 */
.hero-tag    { animation: hero-fade-up 0.5s ease-out 0.55s both; }
.hero-title  { animation: hero-fade-up 0.6s ease-out 0.65s both; }
.hero-desc   { animation: hero-fade-up 0.6s ease-out 0.75s both; }
.hero-stats  { animation: hero-fade-up 0.6s ease-out 0.85s both; }

/* 右侧优惠券面板及卡片错落淡入 */
.hero-coupon-panel { animation: hero-fade-up 0.6s ease-out 0.65s both; }
.hero-coupon-card  { animation: hero-fade-up 0.5s ease-out both; }
.hero-coupon-card:nth-child(1) { animation-delay: 0.75s; }
.hero-coupon-card:nth-child(2) { animation-delay: 0.85s; }
.hero-coupon-card:nth-child(3) { animation-delay: 0.95s; }
.hero-coupon-card:nth-child(4) { animation-delay: 1.05s; }

/* 数字区域轻微呼吸高亮 */
@keyframes hero-stat-glow {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.04); }
}
.hero-stat:nth-child(1) span:first-child {
  animation: hero-stat-glow 3s ease-in-out infinite;
}
</style>