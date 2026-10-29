<script setup>
import { ref, computed, onMounted, reactive, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { fetchProductById, fetchSkusByProduct } from "@/api/product";
import { fetchCategories } from "@/api/product";
import { useCartStore } from "@/store/cart";
import { useUserStore } from "@/store/user";
import { reportBehavior } from "@/api/behavior";
import {
  fetchProductReviews,
  checkReviewEligibility,
  submitReview,
} from "@/api/review";
import FrontendLayout from "@/components/FrontendLayout.vue";
import Breadcrumb from "@/components/Breadcrumb.vue";
import { useRecommendStore } from "@/store/recommend";
import { ShoppingCart, Zap, Heart, Shield, Award, RotateCcw, Star, Sparkles } from "lucide-vue-next";

const route = useRoute();
const router = useRouter();
const cartStore = useCartStore();
const userStore = useUserStore();
const recommendStore = useRecommendStore();

// F-R-006 详情页"猜你喜欢"
const similarProducts = ref([]);

const product = ref(null);
const loading = ref(true);
const selectedSpecId = ref(null);
const quantity = ref(1);
const isFavorite = ref(false);

// ========== 评价（F-U-061 ~ F-U-063） ==========
const reviewPage = ref({ total: 0, positiveRate: 0, averageRating: 0, items: [] });
const reviewLoading = ref(false);
const showReviewForm = ref(false);
const submittingReview = ref(false);
const myRating = ref(5);
const myContent = ref("");
const myHoverRating = ref(0);
const eligibility = ref({ canReview: false, reason: "NOT_LOGGED_IN" });
const eligibilityLoaded = ref(false);
const reviewErrorMsg = ref("");

const positiveRateText = computed(() => {
  const r = reviewPage.value.positiveRate || 0;
  return (r * 100).toFixed(1) + "%";
});

const avgRatingText = computed(() => {
  const a = reviewPage.value.averageRating || 0;
  return a.toFixed(1);
});

const reviewCountText = computed(() => reviewPage.value.total || 0);

// 评价样本过少（< 3）时综合评分/好评率回归到商品主表数据，避免单条极端评价主导显示
const REVIEW_SAMPLE_THRESHOLD = 3;
const displayRating = computed(() => {
  if (reviewCountText.value >= REVIEW_SAMPLE_THRESHOLD) return avgRatingText.value;
  return product.value && product.value.rating != null ? Number(product.value.rating).toFixed(1) : "4.6";
});
const displayPositiveRate = computed(() => {
  if (reviewCountText.value >= REVIEW_SAMPLE_THRESHOLD) return positiveRateText.value;
  return "--";
});

const eligibilityHint = computed(() => {
  switch (eligibility.value.reason) {
    case "NOT_LOGGED_IN":   return "登录后即可评价";
    case "NOT_PURCHASED":   return "仅购买过该商品的用户可评价";
    case "ALREADY_REVIEWED":return "您已评价过该商品，每位用户仅可评价一次";
    default:                return userStore.isLoggedIn ? "正在加载评价资格..." : "登录后即可评价";
  }
});

const canSubmitReview = computed(() =>
  eligibility.value.canReview &&
  myRating.value >= 1 &&
  myContent.value.trim().length >= 5
);

onMounted(() => {
  loadProduct(route.params.id);
});

// 修复"猜你喜欢"点击同类目商品不刷新：
// 路由参数变化但组件被复用时 onMounted 不会重跑，必须 watch
watch(() => route.params.id, (newId) => {
  if (newId && Number(newId) !== currentId.value) {
    loadProduct(newId);
  }
});

/** 当前正在展示的商品 id（避免 onMounted 与 watch 初始触发重复拉取） */
const currentId = ref(null);

async function loadProduct(id) {
  if (!id) return;
  currentId.value = Number(id);
  // 重置瞬态：避免上一个商品的 selectedSpecId / isFavorite / quantity 残留
  loading.value = true;
  product.value = null;
  selectedSpecId.value = null;
  quantity.value = 1;
  isFavorite.value = false;
  reviewPage.value = { total: 0, positiveRate: 0, averageRating: 0, items: [] };
  similarProducts.value = [];
  // 复用同一组件时滚到顶部
  window.scrollTo({ top: 0, behavior: "instant" });

  // 已登录时从服务端同步收藏列表（解决 localStorage 与服务端不一致）
  if (userStore.isLoggedIn) {
    userStore.loadFavoritesFromServer().then(() => {
      if (product.value && Number(product.value.id) === currentId.value) {
        isFavorite.value = userStore.isFavorite(product.value.id);
      }
    });
  }
  try {
    const res = await fetchProductById(id);
    let skus = [];
    try {
      skus = await fetchSkusByProduct(id);
    } catch (e) {
      skus = [];
    }
    const categoryMap = await loadCategoryName(res.categoryId);

    const merged = {
      ...res,
      image: res.coverImage,
      skuList: (skus || []).map(s => ({
        skuId: s.id,
        sku_id: s.id,
        spec_name: s.specName,
        specName: s.specName,
        price: Number(s.price || res.price || 0),
        stock: s.stock ?? 0,
      })),
      category: categoryMap,
    };
    // 防止慢请求回来时用户已经跳到别的商品
    if (Number(id) !== currentId.value) return;
    product.value = merged;
    if (merged.skuList.length > 0) {
      selectedSpecId.value = merged.skuList[0].skuId;
    }
    isFavorite.value = userStore.isFavorite(merged.id);
    reportBehavior("VIEW", merged.id, { source: "detail", category: categoryMap });
    // 拉评价列表 + 资格预检（在 try 内，依赖 product.id）
    await Promise.all([loadReviews(), loadEligibility()]);
    // 拉"猜你喜欢"——ItemCF 相似商品（独立 try，失败不影响主信息展示）
    try {
      const sim = await recommendStore.loadSimilar(merged.id, 6);
      if (Number(id) === currentId.value) {
        similarProducts.value = sim;
      }
    } catch (e) {
      similarProducts.value = [];
    }
  } catch (e) {
    console.error(e);
    if (Number(id) === currentId.value) {
      product.value = null;
    }
  } finally {
    if (Number(id) === currentId.value) {
      loading.value = false;
    }
  }
}

const _categoryCache = ref({});
async function loadCategoryName(cid) {
  if (!cid) return "其他";
  if (_categoryCache.value[cid]) return _categoryCache.value[cid];
  try {
    const list = await fetchCategories();
    const map = {};
    (list || []).forEach(c => { map[c.id] = c.name; });
    _categoryCache.value = map;
    return map[cid] || "其他";
  } catch {
    return "其他";
  }
}

const currentSku = computed(() => {
  if (!product.value || !product.value.skuList) return null;
  return product.value.skuList.find(sku => sku.skuId === selectedSpecId.value) || product.value.skuList[0] || null;
});

const displayPrice = computed(() => {
  if (currentSku.value) return Number(currentSku.value.price || 0);
  return product.value ? Number(product.value.price || 0) : 0;
});

const displayMarketPrice = computed(() => {
  if (!product.value) return 0;
  const base = Number(product.value.marketPrice || product.value.price || 0);
  return base;
});

const displayStock = computed(() => {
  if (currentSku.value) return currentSku.value.stock;
  return product.value ? (product.value.inventory || 0) : 0;
});

const specNames = computed(() => {
  if (!product.value) return [];
  return (product.value.skuList || []).map(s => ({ id: s.skuId, name: s.specName }));
});

const breadcrumbs = computed(() => {
  if (!product.value) return [];
  return [
    { label: product.value.category, path: "/" },
    { label: product.value.name }
  ];
});

const handleQuantityChange = (change) => {
  const nextVal = quantity.value + change;
  if (nextVal >= 1 && nextVal <= Math.max(displayStock.value, 1)) {
    quantity.value = nextVal;
  }
};

const addToCart = async () => {
  if (!product.value || !currentSku.value) return;
  if (!userStore.isLoggedIn) {
    router.push("/login");
    return;
  }
  reportBehavior("ADD_CART", product.value.id, {
    spec: currentSku.value.specName,
    quantity: quantity.value,
    source: "detail"
  });
  try {
    await cartStore.addToCart(
      { ...product.value, skuId: currentSku.value.skuId },
      currentSku.value.skuId,
      quantity.value
    );
    showToast.value = true;
    setTimeout(() => { showToast.value = false; }, 2500);
  } catch (e) {
    if (e.code === 401 || e.code === 403 || e.code === 1002) { router.push("/login"); return; }
    alert(e.message || "加入购物车失败，请稍后再试");
  }
};

const buyNow = async () => {
  if (!product.value || !currentSku.value) return;
  if (!userStore.isLoggedIn) {
    router.push("/login");
    return;
  }
  reportBehavior("ADD_CART", product.value.id, {
    spec: currentSku.value.specName,
    quantity: quantity.value,
    source: "detail_buy_now"
  });
  try {
    await cartStore.addToCart(
      { ...product.value, skuId: currentSku.value.skuId },
      currentSku.value.skuId,
      quantity.value
    );
    router.push("/cart");
  } catch (e) {
    if (e.code === 401 || e.code === 403 || e.code === 1002) { router.push("/login"); return; }
    alert(e.message || "结算失败，请稍后再试");
  }
};

const toggleFav = () => {
  if (!product.value) return;
  if (!userStore.isLoggedIn) {
    router.push("/login");
    return;
  }
  const wasFavorite = isFavorite.value;
  // toggleFavorite 内部已经做了乐观更新 + 后端调用 + 失败回滚
  const nowFavorite = userStore.toggleFavorite(product.value.id);
  isFavorite.value = userStore.isFavorite(product.value.id);
  // 上报埋点
  reportBehavior("FAVORITE", product.value.id, {
    action: nowFavorite ? "add" : "remove",
    source: "detail"
  });
  // 反馈 toast
  toastMessage.value = nowFavorite
    ? "已加入收藏，可在「我的收藏」查看"
    : "已取消收藏";
  showToast.value = true;
  setTimeout(() => { showToast.value = false; }, 2000);
};

const showToast = ref(false);
const toastMessage = ref("");

// ========== 评价函数 ==========
const loadReviews = async () => {
  if (!product.value) return;
  reviewLoading.value = true;
  try {
    const data = await fetchProductReviews(product.value.id, { page: 1, size: 10 });
    reviewPage.value = data || { total: 0, positiveRate: 0, averageRating: 0, items: [] };
  } catch (e) {
    console.error("[review] list fetch failed", e);
  } finally {
    reviewLoading.value = false;
  }
};

const loadEligibility = async () => {
  if (!product.value) return;
  eligibilityLoaded.value = false;
  try {
    const data = await checkReviewEligibility(product.value.id);
    eligibility.value = data || { canReview: false, reason: "NOT_LOGGED_IN" };
  } catch (e) {
    // 401 已被 http 拦截器重定向，这里只处理非登录错误
    eligibility.value = { canReview: false, reason: "NOT_LOGGED_IN" };
  } finally {
    eligibilityLoaded.value = true;
  }
};

const toggleReviewForm = () => {
  if (!eligibility.value.canReview) {
    if (eligibility.value.reason === "NOT_LOGGED_IN") {
      router.push("/login");
    } else {
      toastMessage.value = eligibilityHint.value;
      showToast.value = true;
      setTimeout(() => { showToast.value = false; }, 2200);
    }
    return;
  }
  showReviewForm.value = !showReviewForm.value;
};

const submitMyReview = async () => {
  if (!canSubmitReview.value || submittingReview.value) return;
  submittingReview.value = true;
  try {
    await submitReview({
      productId: product.value.id,
      rating: myRating.value,
      content: myContent.value.trim(),
    });
    reportBehavior("REVIEW", product.value.id, { rating: myRating.value });
    toastMessage.value = "评价成功，感谢您的反馈！";
    showToast.value = true;
    setTimeout(() => { showToast.value = false; }, 2200);
    // 刷新：列表 + 资格（防重规则会让 canReview 立刻变 false）
    await Promise.all([loadReviews(), loadEligibility()]);
    // 重置表单
    showReviewForm.value = false;
    myRating.value = 5;
    myContent.value = "";
  } catch (e) {
    if (e.code === 401 || e.code === 1002) { router.push("/login"); return; }
    if (e.code === 6010) toastMessage.value = "仅购买过该商品的用户可评价";
    else if (e.code === 6011) toastMessage.value = "您已评价过该商品";
    else toastMessage.value = e.message || "评价失败，请稍后再试";
    showToast.value = true;
    setTimeout(() => { showToast.value = false; }, 2200);
  } finally {
    submittingReview.value = false;
  }
};
</script>

<template>
  <FrontendLayout>
    <!-- Simple Toast -->
    <Transition name="fade">
      <div 
        v-if="showToast" 
        class="fixed top-20 right-8 z-50 bg-[#004e64] text-white px-5 py-3.5 rounded-xl shadow-lg border border-[#e0f2f1]/10 flex items-center gap-2 text-xs animate-bounce"
      >
        <span class="material-symbols-outlined text-green-400 text-lg">check_circle</span>
        <span>{{ toastMessage || '添加购物车成功！可以前往结算啦~' }}</span>
      </div>
    </Transition>

    <div class="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-6">
      
      <!-- Breadcrumbs -->
      <Breadcrumb v-if="product" :items="breadcrumbs" class="mb-6" />

      <!-- Loading and Error State -->
      <div v-if="loading" class="flex flex-col items-center justify-center py-24">
        <span class="material-symbols-outlined text-stone-300 text-5xl animate-spin">progress_activity</span>
        <p class="text-sm text-stone-500 mt-2">美味加载中，请稍候...</p>
      </div>

      <div v-else-if="!product" class="flex flex-col items-center justify-center py-20 bg-white border border-[#e4e2de] rounded-3xl">
        <span class="material-symbols-outlined text-stone-300 text-6xl">cookie_off</span>
        <h4 class="text-base font-bold text-stone-700 mt-3">商品不存在或已下架</h4>
        <button @click="router.push('/')" class="mt-4 px-4 py-2 bg-[#ff6b35] text-white text-xs font-semibold rounded-xl cursor-pointer">
          回到首页
        </button>
      </div>

      <!-- Detail Card -->
      <div v-else class="grid grid-cols-1 md:grid-cols-12 gap-8 bg-white border border-[#e4e2de] rounded-3xl p-6 sm:p-8 shadow-sm">
        
        <!-- Left Side: Image Gallery -->
        <div class="md:col-span-5 flex flex-col gap-4">
          <div class="aspect-square w-full rounded-2xl overflow-hidden bg-stone-100 border border-stone-200/60 shadow-sm">
            <img 
              :src="product.image" 
              :alt="product.name" 
              class="w-full h-full object-cover"
            />
          </div>

          <!-- trust tags -->
          <div class="grid grid-cols-3 gap-3 bg-[#fbf9f5] rounded-xl p-3 border border-[#e4e2de]">
            <div class="flex items-center gap-1 text-[10px] text-stone-600 justify-center">
              <Shield class="w-3.5 h-3.5 text-stone-400 shrink-0" />
              <span>品质无忧</span>
            </div>
            <div class="flex items-center gap-1 text-[10px] text-stone-600 justify-center">
              <Award class="w-3.5 h-3.5 text-stone-400 shrink-0" />
              <span>原产地严选</span>
            </div>
            <div class="flex items-center gap-1 text-[10px] text-stone-600 justify-center">
              <RotateCcw class="w-3.5 h-3.5 text-stone-400 shrink-0" />
              <span>无忧退换</span>
            </div>
          </div>
        </div>

        <!-- Right Side: Details & Buying interface -->
        <div class="md:col-span-7 flex flex-col justify-between">
          <div>
            <!-- Header elements -->
            <div class="flex items-center justify-between gap-2 mb-2">
              <span class="px-2.5 py-0.5 bg-[#ffdbcc] text-[#ff6b35] text-[10px] font-bold rounded-md">
                {{ product.category }}
              </span>
              <span class="text-xs text-stone-400">已售出 {{ product.sales }} 件</span>
            </div>

            <h1 class="text-xl sm:text-2xl font-black text-stone-800 leading-snug">
              {{ product.name }}
            </h1>
            <p class="text-xs sm:text-sm text-stone-500 mt-2 leading-relaxed">
              {{ product.description }}
            </p>

            <!-- Price container -->
            <div class="bg-[#fbf9f5] rounded-2xl p-4 border border-[#e4e2de] mt-4 flex items-center justify-between gap-4">
              <div>
                <span class="text-[10px] text-stone-400 block font-semibold mb-1">优惠价</span>
                <div class="flex items-baseline gap-2">
                  <span class="text-2xl font-black text-[#ff6b35] font-display">￥{{ displayPrice.toFixed(1) }}</span>
                  <span class="text-xs text-stone-400 line-through">￥{{ displayMarketPrice.toFixed(1) }}</span>
                </div>
              </div>
              <div class="text-right border-l border-stone-200 pl-4 shrink-0">
                <span class="text-[10px] text-stone-400 block font-semibold mb-1">综合评分</span>
                <div class="flex items-center gap-1 justify-end">
                  <span class="material-symbols-outlined text-[#ff6b35] text-base fill-current">star</span>
                  <span class="text-sm font-bold text-stone-800">{{ displayRating }} / 5.0</span>
                </div>
              </div>
            </div>

            <!-- Specs Selector -->
            <div class="mt-5">
              <h4 class="text-xs font-bold text-stone-600 mb-2">选择规格</h4>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="spec in specNames"
                  :key="spec.id"
                  @click="selectedSpecId = spec.id"
                  :class="`px-3 py-2 text-xs font-semibold rounded-xl border transition-all cursor-pointer ${
                    selectedSpecId === spec.id
                      ? 'border-[#ff6b35] bg-[#ffdbcc] text-[#ff6b35]'
                      : 'border-[#e4e2de] hover:border-stone-400 text-stone-700'
                  }`"
                >
                  {{ spec.name || '默认规格' }}
                </button>
              </div>
            </div>

            <!-- Quantity counter -->
            <div class="mt-5 flex items-center gap-4">
              <h4 class="text-xs font-bold text-stone-600">购买数量</h4>
              
              <div class="flex items-center border border-[#e4e2de] rounded-xl bg-white p-1">
                <button 
                  @click="handleQuantityChange(-1)"
                  class="w-8 h-8 rounded-lg flex items-center justify-center hover:bg-[#efeeea] text-stone-600 font-bold text-xs cursor-pointer"
                >
                  -
                </button>
                <span class="w-10 text-center text-xs font-bold text-stone-800 font-mono">{{ quantity }}</span>
                <button 
                  @click="handleQuantityChange(1)"
                  class="w-8 h-8 rounded-lg flex items-center justify-center hover:bg-[#efeeea] text-stone-600 font-bold text-xs cursor-pointer"
                >
                  +
                </button>
              </div>

              <span class="text-[11px] text-stone-400">（库存剩于 {{ displayStock }} 件）</span>
            </div>
          </div>

          <!-- Buy actions -->
          <div class="mt-8 flex flex-col sm:flex-row items-center gap-3 border-t border-stone-100 pt-6">
            <!-- Favorite button -->
            <button
              @click="toggleFav"
              :title="isFavorite ? '取消收藏' : '加入收藏'"
              :class="`w-12 h-12 rounded-xl flex items-center justify-center border transition-all cursor-pointer ${
                isFavorite
                  ? 'border-red-200 bg-red-50 text-red-500'
                  : 'border-[#e4e2de] hover:border-stone-400 text-stone-500 hover:text-red-500'
              }`"
            >
              <Heart :class="`w-5 h-5 transition-transform ${isFavorite ? 'fill-current scale-110' : ''}`" />
            </button>

            <!-- Add to cart -->
            <button
              @click="addToCart"
              class="flex-1 w-full bg-white text-[#ff6b35] hover:bg-[#ff6b35]/5 border border-[#ff6b35] py-3.5 px-6 rounded-xl font-bold text-sm flex items-center justify-center gap-2 transform active:scale-95 transition-all cursor-pointer"
            >
              <ShoppingCart class="w-4.5 h-4.5" />
              <span>加入购物车</span>
            </button>

            <!-- Buy now -->
            <button
              @click="buyNow"
              class="flex-1 w-full bg-[#ff6b35] text-white hover:bg-[#ff6b35]/90 py-3.5 px-6 rounded-xl font-bold text-sm flex items-center justify-center gap-2 transform active:scale-95 transition-all cursor-pointer shadow-sm"
            >
              <Zap class="w-4.5 h-4.5 fill-current" />
              <span>立即结算</span>
            </button>
          </div>

        </div>
      </div>

      <!-- Product Description & Reviews Tabs -->
      <div v-if="product" class="grid grid-cols-1 lg:grid-cols-12 gap-8 mt-10">
        <!-- left parameters -->
        <div class="lg:col-span-5 bg-white border border-[#e4e2de] rounded-3xl p-6 shadow-sm">
          <h3 class="text-sm font-bold text-stone-800 mb-4 flex items-center gap-1.5 border-b border-stone-100 pb-3">
            <span class="material-symbols-outlined text-[#ff6b35] text-base leading-none">menu_book</span>
            <span>商品参数</span>
          </h3>

          <div class="space-y-3.5 text-xs text-stone-600">
            <div class="flex items-center justify-between py-1.5 border-b border-stone-100">
              <span class="text-stone-400 font-medium">原产地</span>
              <span class="font-bold text-stone-700">{{ product.origin || '—' }}</span>
            </div>
            <div class="flex items-center justify-between py-1.5 border-b border-stone-100">
              <span class="text-stone-400 font-medium">保质期</span>
              <span class="font-bold text-stone-700">{{ product.shelfLife || '—' }}</span>
            </div>
            <div class="flex items-center justify-between py-1.5 border-b border-stone-100">
              <span class="text-stone-400 font-medium">净含量</span>
              <span class="font-bold text-stone-700">{{ product.weight || '—' }}</span>
            </div>
            <div class="flex flex-col gap-1 py-1.5">
              <span class="text-stone-400 font-medium">配料表</span>
              <span class="font-bold text-stone-700 leading-relaxed bg-[#fbf9f5] p-2.5 rounded-lg border border-[#e4e2de] mt-1">{{ product.ingredients || '—' }}</span>
            </div>
          </div>
        </div>

        <!-- right comments list -->
        <div class="lg:col-span-7 bg-white border border-[#e4e2de] rounded-3xl p-6 shadow-sm">
          <h3 class="text-sm font-bold text-stone-800 mb-4 flex items-center justify-between border-b border-stone-100 pb-3">
            <div class="flex items-center gap-1.5">
              <span class="material-symbols-outlined text-[#ff6b35] text-base leading-none">rate_review</span>
              <span>吃货点评 ({{ reviewCountText }})</span>
            </div>
            <span class="text-[11px] text-stone-400">综合好评率 {{ displayPositiveRate }}</span>
          </h3>

          <!-- 评价操作栏：资格提示 + 写评价按钮 -->
          <div class="flex items-center justify-between gap-2 mb-4 px-3 py-2.5 bg-[#fbf9f5] rounded-xl border border-[#e4e2de]">
            <div class="text-[11px] text-stone-500 flex items-center gap-1.5">
              <span class="material-symbols-outlined text-[14px] text-stone-400">info</span>
              <span>{{ eligibilityHint }}</span>
            </div>
            <button
              v-if="eligibilityLoaded"
              @click="toggleReviewForm"
              :disabled="!eligibility.canReview"
              :class="`px-3.5 py-1.5 text-[11px] font-bold rounded-lg transition-all ${
                eligibility.canReview
                  ? 'bg-[#ff6b35] text-white hover:bg-[#ff6b35]/90 cursor-pointer shadow-sm'
                  : 'bg-stone-200 text-stone-400 cursor-not-allowed'
              }`"
            >
              {{ showReviewForm ? '收起' : (eligibility.canReview ? '写评价' : '无法评价') }}
            </button>
          </div>

          <!-- 评价提交表单（折叠） -->
          <div v-if="showReviewForm && eligibility.canReview" class="mb-5 p-4 bg-white border border-[#ffdbcc] rounded-2xl">
            <div class="flex items-center gap-3 mb-3">
              <span class="text-[11px] font-bold text-stone-600">您的评分</span>
              <div class="flex gap-1" @mouseleave="myHoverRating = 0">
                <button
                  v-for="n in 5"
                  :key="n"
                  type="button"
                  @click="myRating = n"
                  @mouseenter="myHoverRating = n"
                  class="cursor-pointer p-0.5 transition-transform hover:scale-110"
                  :title="n + ' 星'"
                >
                  <Star
                    :class="`w-5 h-5 ${(myHoverRating || myRating) >= n ? 'text-[#ff6b35] fill-[#ff6b35]' : 'text-stone-300 fill-none'}`"
                  />
                </button>
              </div>
              <span class="text-[11px] text-stone-400 ml-1">{{ myHoverRating || myRating }} / 5</span>
            </div>
            <textarea
              v-model="myContent"
              maxlength="500"
              rows="4"
              placeholder="聊聊您的食用体验，对其他吃货很有帮助～（5-500 字）"
              class="w-full text-xs text-stone-700 px-3 py-2.5 border border-[#e4e2de] rounded-xl resize-none focus:outline-none focus:border-[#ff6b35] bg-[#fbf9f5]"
            />
            <div class="flex items-center justify-between mt-2.5">
              <span class="text-[10px] text-stone-400">{{ myContent.trim().length }} / 500</span>
              <button
                @click="submitMyReview"
                :disabled="!canSubmitReview || submittingReview"
                :class="`px-4 py-2 text-xs font-bold rounded-lg transition-all ${
                  canSubmitReview && !submittingReview
                    ? 'bg-[#ff6b35] text-white hover:bg-[#ff6b35]/90 cursor-pointer shadow-sm'
                    : 'bg-stone-200 text-stone-400 cursor-not-allowed'
                }`"
              >
                {{ submittingReview ? '提交中…' : '发布评价' }}
              </button>
            </div>
          </div>

          <!-- 评价列表 -->
          <div v-if="reviewLoading" class="py-10 flex items-center justify-center text-stone-400 text-xs">
            <span class="material-symbols-outlined text-lg animate-spin mr-1">progress_activity</span>
            加载评价中…
          </div>
          <div v-else-if="reviewPage.items.length === 0" class="py-12 text-center">
            <span class="material-symbols-outlined text-stone-300 text-4xl">chat_bubble</span>
            <p class="text-xs text-stone-400 mt-2">暂无评价，来做第一个吃螃蟹的人吧～</p>
          </div>
          <div v-else class="divide-y divide-stone-100">
            <div
              v-for="(comment, idx) in reviewPage.items"
              :key="comment.id || idx"
              class="py-4 first:pt-0 last:pb-0"
            >
              <div class="flex items-center justify-between gap-2 mb-2">
                <div class="flex items-center gap-2">
                  <div class="w-6.5 h-6.5 rounded-full overflow-hidden bg-stone-100 flex items-center justify-center border border-stone-200">
                    <img v-if="comment.avatar && !comment.anonymous" :src="comment.avatar" class="w-full h-full object-cover" />
                    <span v-else class="material-symbols-outlined text-stone-400 text-sm">person</span>
                  </div>
                  <span class="text-xs font-bold text-stone-700">{{ comment.displayName }}</span>
                  <div class="flex gap-0.5 ml-1">
                    <span
                      v-for="s in 5"
                      :key="s"
                      :class="`material-symbols-outlined text-[10px] ${s <= (comment.rating || 0) ? 'text-[#ff6b35] fill-current' : 'text-stone-200'}`"
                    >
                      star
                    </span>
                  </div>
                </div>
                <span class="text-[10px] text-stone-400 font-mono">{{ comment.createdAt }}</span>
              </div>
              <p class="text-xs text-stone-600 leading-relaxed pl-8">
                {{ comment.content }}
              </p>
            </div>
          </div>
        </div>
      </div>

    </div>

    <!-- F-R-006 详情页"猜你喜欢"——ItemCF 相似商品（基于 Redis 缓存的物品相似度） -->
    <div
      v-if="similarProducts.length > 0"
      class="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 pb-8"
    >
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-5 shadow-sm">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-xl bg-gradient-to-br from-[#ffdbcc] to-[#ffebe3] flex items-center justify-center">
              <Sparkles class="w-4 h-4 text-[#ff6b35]" />
            </div>
            <div>
              <h2 class="text-sm font-bold text-stone-800 font-display flex items-center gap-1.5">
                猜你喜欢
                <span class="text-[10px] font-mono font-normal text-[#ff6b35] bg-[#ffebe3] border border-[#ffdbcc] px-1.5 py-0.5 rounded-md">
                  ItemCF · Redis
                </span>
              </h2>
              <p class="text-[11px] text-stone-400 mt-0.5">基于物品协同过滤，从近 30 天用户行为中挖掘</p>
            </div>
          </div>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          <div
            v-for="p in similarProducts"
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
</style>
