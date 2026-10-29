<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import { useCartStore } from "@/store/cart";
import { fetchProducts, fetchCategories } from "@/api/product";
import { reportBehavior } from "@/api/behavior";
import FrontendLayout from "@/components/FrontendLayout.vue";
import { ArrowLeft, Heart, ShoppingCart, Tag } from "lucide-vue-next";

const router = useRouter();
const userStore = useUserStore();
const cartStore = useCartStore();

const showToast = ref(false);
const toastMessage = ref("");
const allProducts = ref([]);
const categoryMap = ref({});

onMounted(async () => {
  await userStore.loadUser();
  // 从服务端同步收藏列表（解决 localStorage 与 DB 不一致）
  await userStore.loadFavoritesFromServer();
  // 拉全部商品 + 分类
  try {
    const [prods, cats] = await Promise.all([fetchProducts(), fetchCategories()]);
    categoryMap.value = (cats || []).reduce((m, c) => { m[c.id] = c.name; return m; }, {});
    allProducts.value = (prods || []).map(p => ({
      ...p,
      image: p.coverImage,         // 后端字段 coverImage → 前端 image
      category: categoryMap.value[p.categoryId] || "其他",
    }));
  } catch (e) {
    console.error(e);
  }
  await cartStore.loadCart();
});

const favoriteProducts = computed(() => {
  const favSet = new Set(userStore.favorites);
  return allProducts.value.filter(p => favSet.has(p.id));
});

const handleRemoveFavorite = (productId) => {
  // F-R-001: 取消收藏也作为一条 FAVORITE 行为埋点
  reportBehavior("FAVORITE", productId, { action: "remove", source: "favorites_page" });
  userStore.toggleFavorite(productId);
  triggerToast("已取消收藏该商品");
};

const handleAddToCart = async (product) => {
  // 通过 SKU 接口拿到首个 skuId（详情页同款流程）
  const defaultSpec = (product.specs && product.specs[0]) || "150g";
  reportBehavior("ADD_CART", product.id, { spec: defaultSpec, quantity: 1, source: "favorites_add" });
  try {
    // 收藏页没有 SKU 信息时，给个降级：直接传 productId 让 cartService 按首个 SKU 补价
    await cartStore.addToCart(
      { ...product, skuId: product.skuId || null },
      product.skuId || defaultSpec,
      1
    );
    triggerToast(`已将 1 件「${product.name}」加入购物车！`);
  } catch (e) {
    if (e.code === 401 || e.code === 1002) { router.push("/login"); return; }
    triggerToast(e.message || "加入购物车失败");
  }
};

const triggerToast = (msg) => {
  toastMessage.value = msg;
  showToast.value = true;
  setTimeout(() => {
    showToast.value = false;
  }, 2500);
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

    <div class="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
      
      <!-- Page Header -->
      <div class="flex items-center gap-3 mb-8">
        <button 
          @click="router.push('/profile')" 
          class="p-2 bg-white border border-[#e4e2de] hover:bg-[#efeeea] rounded-xl text-stone-600 transition-colors cursor-pointer animate-pulse-subtle"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-xl font-bold tracking-tight text-stone-800 font-display">
            我的商品收藏夹
          </h1>
          <p class="text-xs text-stone-500 mt-0.5">
            珍藏您最爱的美味零食，好货不迷路
          </p>
        </div>
      </div>

      <!-- No Favorites Empty State -->
      <div v-if="favoriteProducts.length === 0" class="bg-white border border-[#e4e2de] rounded-3xl p-16 text-center shadow-sm">
        <Heart class="w-12 h-12 text-stone-300 mx-auto mb-3" />
        <h3 class="text-sm font-bold text-stone-700">您的收藏夹空空如也</h3>
        <p class="text-xs text-stone-400 mt-1">去首页逛一逛，收藏美味的零食吧</p>
        <button 
          @click="router.push('/')" 
          class="mt-4 px-4.5 py-2.5 bg-[#ff6b35] text-white font-bold rounded-xl text-xs hover:bg-[#ff6b35]/90 cursor-pointer shadow-sm"
        >
          挑选好物
        </button>
      </div>

      <!-- Favorites Grid -->
      <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        <div
          v-for="product in favoriteProducts"
          :key="product.id"
          class="group bg-white border border-[#e4e2de] rounded-2xl overflow-hidden hover:shadow-md hover:border-[#ff6b35]/20 transition-all duration-300 flex flex-col justify-between"
        >
          <!-- image wrapper -->
          <div 
            @click="router.push(`/product/${product.id}`)"
            class="relative aspect-[4/3] w-full overflow-hidden bg-stone-100 cursor-pointer"
          >
            <img
              :src="product.image || product.coverImage"
              :alt="product.name"
              class="object-cover w-full h-full transform transition-transform duration-500 group-hover:scale-105"
            />
            
            <div class="absolute top-3 left-3">
              <span class="px-2 py-0.5 text-[9px] font-bold text-white bg-black/60 rounded-md backdrop-blur-sm">
                {{ product.category }}
              </span>
            </div>

            <!-- rating -->
            <div class="absolute bottom-3 right-3 bg-white/95 backdrop-blur-sm px-2 py-0.5 rounded-lg text-[9px] font-bold text-stone-800 flex items-center gap-0.5 shadow-sm">
              <span class="material-symbols-outlined text-[11px] text-[#ff6b35] fill-current">star</span>
              <span>{{ product.rating }}</span>
            </div>
          </div>

          <!-- info -->
          <div class="p-5 flex-1 flex flex-col justify-between">
            <div>
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

            <!-- prices and actions -->
            <div class="mt-4 flex items-center justify-between border-t border-stone-100 pt-3">
              <div class="flex items-baseline gap-1.5">
                <span class="text-[#ff6b35] text-sm font-black font-display">
                  ￥{{ product.price.toFixed(1) }}
                </span>
                <span class="text-stone-400 text-[10px] line-through">
                  ￥{{ product.marketPrice.toFixed(1) }}
                </span>
              </div>

              <!-- buttons -->
              <div class="flex items-center gap-1.5">
                <!-- Favorite toggle (lit when in favorites) -->
                <button
                  @click="handleRemoveFavorite(product.id)"
                  class="w-7 h-7 rounded-lg border border-red-200 bg-red-50 hover:bg-white hover:text-red-500 text-red-500 flex items-center justify-center transition-colors cursor-pointer"
                  title="取消收藏"
                >
                  <Heart class="w-3.5 h-3.5 fill-current" />
                </button>

                <!-- Add to Cart -->
                <button
                  @click="handleAddToCart(product)"
                  class="w-7 h-7 rounded-lg bg-[#ff6b35]/10 text-[#ff6b35] hover:bg-[#ff6b35] hover:text-white flex items-center justify-center transition-all cursor-pointer"
                  title="加入购物车"
                >
                  <ShoppingCart class="w-3.5 h-3.5" />
                </button>
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
