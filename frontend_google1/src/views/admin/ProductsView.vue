<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useAdminStore } from "@/store/admin";
import AdminLayout from "@/components/AdminLayout.vue";
import Pagination from "@/components/Pagination.vue";
import { Plus, Trash2, Search, X, PackageOpen, AlertCircle, Pencil, RefreshCw, ImagePlus } from "lucide-vue-next";
import { fetchImageListApi, fetchProductSkusApi } from "@/api/admin";


const adminStore = useAdminStore();

const searchKeyword = ref("");
const selectedCategory = ref("全部");
const currentPage = ref(1);
const pageSize = 12;

// Modal states — F-A-021 新增 / F-A-022 编辑 共用一个 dialog
const showAddModal = ref(false);
const editingId = ref(null); // null = 新增模式，否则为商品 id
const newName = ref("");
const newPrice = ref(0);
const newMarketPrice = ref(0);
const newCategory = ref("坚果炒货");
const newDescription = ref("");
const newImage = ref("");
const newOrigin = ref("");
const newShelfLife = ref("");
const newWeight = ref("");
const newIngredients = ref("");

// 规格动态行：每个规格独立 名称/价格/库存（编辑态带 id 用于后端按 id 同步）
const newSpecRows = ref([]);
const addSpecRow = () => { newSpecRows.value.push({ id: null, specName: "", price: null, stock: 200 }); };
const removeSpecRow = (idx) => {
  if (newSpecRows.value.length <= 1) { newSpecRows.value[0] = { id: null, specName: "", price: null, stock: 200 }; return; }
  newSpecRows.value.splice(idx, 1);
};

// 图片库路径选择器 — 上架/编辑商品时从后端本地图片库选配图，选中的 /img/** 路径自动填入 newImage
const showImagePicker = ref(false);
const imageList = ref([]);
const imageLoading = ref(false);

const openImagePicker = async () => {
  showImagePicker.value = true;
  if (imageList.value.length) return; // 已加载过则不重复拉取
  imageLoading.value = true;
  try {
    imageList.value = (await fetchImageListApi()) || [];
  } catch (e) {
    alert("加载图片库失败：" + (e.message || e));
  } finally {
    imageLoading.value = false;
  }
};
const pickImage = (img) => {
  newImage.value = img.url; // e.g. "/img/1/00.jpg"
  showImagePicker.value = false;
};
const closeImagePicker = () => { showImagePicker.value = false; };

const resetForm = () => {
  editingId.value = null;
  newName.value = "";
  newPrice.value = 0;
  newMarketPrice.value = 0;
  newDescription.value = "";
  newImage.value = "";
  newOrigin.value = "";
  newShelfLife.value = "";
  newWeight.value = "";
  newIngredients.value = "";
  newSpecRows.value = [{ id: null, specName: "", price: null, stock: 200 }];
};

onMounted(async () => {
  await refresh();
});

const refresh = async () => {
  // 先确保 categories 到位，再加载 products（依赖 categoryId → name 映射）
  await adminStore.loadCategories();
  await adminStore.loadAdminData();
};

// 刷新按钮状态：旋转动画 + 完成后短暂显示"已更新"
const refreshing = ref(false);
const justRefreshed = ref(false);
let refreshTimer = null;
const handleRefresh = async () => {
  if (refreshing.value) return;
  refreshing.value = true;
  justRefreshed.value = false;
  try {
    await refresh();
    currentPage.value = 1;
    justRefreshed.value = true;
    if (refreshTimer) clearTimeout(refreshTimer);
    refreshTimer = setTimeout(() => { justRefreshed.value = false; }, 1500);
  } catch (err) {
    alert("刷新失败：" + (err.message || err));
  } finally {
    refreshing.value = false;
  }
};

// 实时把 products 加上 category 名字（如果 store 里还没补上）
const productsWithCategory = computed(() => {
  const catMap = {};
  (adminStore.categories || []).forEach(c => { catMap[c.id] = c.name; });
  return (adminStore.products || []).map(p => ({
    ...p,
    category: p.category || catMap[p.categoryId] || "其他",
  }));
});

const filteredProducts = computed(() => {
  let list = productsWithCategory.value;
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.toLowerCase();
    list = list.filter(p => (p.name || "").toLowerCase().includes(kw));
  }
  if (selectedCategory.value !== "全部") {
    list = list.filter(p => p.category === selectedCategory.value);
  }
  return list;
});

// 前端分页:筛后切片
const pagedProducts = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredProducts.value.slice(start, start + pageSize);
});

// 搜索/筛选变化时回到第 1 页
watch([searchKeyword, selectedCategory], () => { currentPage.value = 1; });

// F-A-022: 打开编辑对话框，回填当前商品数据
const handleEditProduct = async (product) => {
  editingId.value = product.id;
  newName.value = product.name;
  newPrice.value = product.price;
  newMarketPrice.value = product.marketPrice || 0;
  newCategory.value = product.category || "";
  newDescription.value = product.description || "";
  newImage.value = product.coverImage || product.image || "";
  newOrigin.value = product.origin || "";
  newShelfLife.value = product.shelfLife || "";
  newWeight.value = product.weight || "";
  newIngredients.value = product.ingredients || "";
  // 回显 SKU 行（每个规格独立价格/库存）；拉取失败退化为单行商品价
  newSpecRows.value = [{ id: null, specName: "", price: null, stock: 200 }];
  try {
    const skus = (await fetchProductSkusApi(product.id)) || [];
    newSpecRows.value = skus.length
      ? skus.map(s => ({ id: s.id, specName: s.specName, price: Number(s.price), stock: Number(s.stock ?? 200) }))
      : [{ id: null, specName: "", price: null, stock: 200 }];
  } catch {
    newSpecRows.value = (product.specs || []).map(spec => ({
      id: null, specName: spec, price: Number(product.price), stock: 200,
    }));
    if (!newSpecRows.value.length) newSpecRows.value = [{ id: null, specName: "", price: null, stock: 200 }];
  }
  showAddModal.value = true; // SKU 回显完成后再打开弹窗，避免闪烁空行
};

// 提交：根据 editingId 决定新增还是编辑
const handleSaveProduct = async () => {
  if (!newName.value) {
    alert("请正确填写商品名称等必填信息哦");
    return;
  }
  // 校验规格行：至少一行有规格名，且每行价格 > 0
  const rows = newSpecRows.value.filter(r => r.specName && String(r.specName).trim());
  if (!rows.length) {
    alert("请至少填写一个规格（规格名、价格必填）");
    return;
  }
  for (const r of rows) {
    const p = Number(r.price);
    if (!(p > 0)) {
      alert(`规格「${r.specName}」的价格必须大于 0`);
      return;
    }
  }

  // 商品主价取最低规格价（列表页展示用）；用户仍可手动调整"售价"兜底
  const minSkuPrice = Math.min(...rows.map(r => Number(r.price)));
  const basePrice = Number(newPrice.value) > 0 ? Number(newPrice.value) : minSkuPrice;

  const defaultImg = "https://images.unsplash.com/photo-1595855759920-86582396756a?auto=format&fit=crop&q=80&w=400";
  const finalImage = newImage.value.trim() || defaultImg;

  // 找当前选中的 categoryId（来自 categories 列表）
  const catObj = (adminStore.categories || []).find(c => c.name === newCategory.value);
  const categoryId = catObj ? catObj.id : null;

  const product = {
    name: newName.value,
    price: basePrice,
    marketPrice: parseFloat(newMarketPrice.value || basePrice * 1.2),
    description: newDescription.value,
    coverImage: finalImage,
    categoryId,
    origin: newOrigin.value || "原产地",
    shelfLife: newShelfLife.value || "180天",
    weight: newWeight.value || "150g",
    ingredients: newIngredients.value || "配料精选",
    // 剩余件数 = 各规格库存之和（后端会按 SKU 库存重算）
    inventory: rows.reduce((acc, r) => acc + (Number(r.stock) > 0 ? Number(r.stock) : 0), 0),
    status: 1,
  };

  // 每个规格独立 名称/价格/库存；编辑态带 id，后端按 id 更新
  const skus = rows.map(r => ({
    id: r.id || null,
    specName: String(r.specName).trim(),
    price: Number(r.price),
    stock: Number(r.stock) > 0 ? Number(r.stock) : 0,
    sales: 0,
    status: 1,
  }));

  const payload = { product, skus };

  try {
    if (editingId.value) {
      // F-A-022: 编辑模式 — PUT /api/admin/product/{id}（商品本体 + SKU 同步）
      await adminStore.updateProduct(editingId.value, payload);
      alert("商品信息已更新！");
    } else {
      // F-A-021: 新增模式
      await adminStore.addProduct({
        product: { ...payload.product, tags: ["新晋推荐"] },
        skus: payload.skus,
      });
      alert("麦乐迪全新零食上架成功！");
    }
    showAddModal.value = false;
    resetForm();
  } catch (err) {
    alert(err.message);
  }
};

const closeModal = () => {
  showAddModal.value = false;
  resetForm();
};

const handleDeleteProduct = async (id) => {
  if (confirm("确认下架并删除该款零食吗？此操作无法撤销。")) {
    try {
      await adminStore.deleteProduct(id);
      alert("商品下架成功。");
    } catch (e) {
      alert(e.message);
    }
  }
};
</script>

<template>
  <AdminLayout 
    title="商品管理" 
    subtitle="增加、删除或下架麦乐迪商城的零食，编辑实时库存"
    :breadcrumbs="['商品管理']"
  >
    <!-- Actions slots -->
    <template #actions>
      <button
        @click="handleRefresh"
        :disabled="refreshing"
        class="text-xs bg-[#efeeea] hover:bg-stone-200 text-[#5a4136] font-bold py-2 px-3.5 rounded-xl flex items-center gap-1 cursor-pointer transition-colors disabled:opacity-60"
      >
        <RefreshCw :class="refreshing ? 'animate-spin' : ''" class="w-3.5 h-3.5" />
        <span>{{ refreshing ? '刷新中…' : (justRefreshed ? '已更新 ✓' : '刷新') }}</span>
      </button>
      <button
        @click="() => { resetForm(); showAddModal = true; }"
        class="ml-2 text-xs bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold py-2 px-4.5 rounded-xl flex items-center gap-1 cursor-pointer shadow-sm"
      >
        <Plus class="w-4 h-4" />
        <span>上架新零食</span>
      </button>
    </template>

    <!-- Modal Form — 新增 / 编辑 共用 -->
    <div
      v-if="showAddModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
    >
      <div class="bg-white rounded-3xl border border-[#e4e2de] p-6 max-w-lg w-full max-h-[85vh] overflow-y-auto shadow-2xl">
        <div class="flex items-center justify-between border-b border-stone-100 pb-3 mb-4">
          <h3 class="text-sm font-bold text-stone-800 font-display">
            {{ editingId ? '编辑商品信息' : '物理上架新食品' }}
          </h3>
          <button @click="closeModal" class="p-1 rounded-full text-stone-400 hover:bg-stone-100">
            <X class="w-4 h-4" />
          </button>
        </div>

        <div class="space-y-4">
          <!-- Name -->
          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">零食商品名称 *</label>
            <input 
              type="text" 
              v-model="newName" 
              placeholder="e.g., 麦乐迪炭烧腰果" 
              class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]"
            />
          </div>

          <!-- Price grid -->
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">售价 (元) *</label>
              <input 
                type="number" 
                v-model="newPrice" 
                class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]"
              />
            </div>
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">市场价 / 划线原价 (元)</label>
              <input 
                type="number" 
                v-model="newMarketPrice" 
                class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]"
              />
            </div>
          </div>

          <!-- Category -->
          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">分类所属 *</label>
            <select
              v-model="newCategory"
              class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de]"
            >
              <option v-if="!adminStore.categories.length" disabled value="">暂无分类，请先到分类管理新增</option>
              <option v-for="c in adminStore.categories" :key="c.id" :value="c.name">
                {{ c.name }}
              </option>
            </select>
          </div>

          <!-- Specs: 每个规格独立 名称/价格/库存 -->
          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">销售规格（每个规格可独立定价）*</label>
            <div class="space-y-2">
              <div v-for="(row, idx) in newSpecRows" :key="idx" class="flex items-center gap-2">
                <input
                  v-model="row.specName"
                  type="text"
                  placeholder="规格名 e.g. 经典原味 180g"
                  class="flex-1 text-xs px-3 py-2 rounded-xl border border-[#e4e2de]"
                />
                <input
                  v-model.number="row.price"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="价格(元)"
                  class="w-24 text-xs px-2.5 py-2 rounded-xl border border-[#e4e2de]"
                />
                <input
                  v-model.number="row.stock"
                  type="number"
                  min="0"
                  placeholder="库存"
                  class="w-20 text-xs px-2.5 py-2 rounded-xl border border-[#e4e2de]"
                />
                <button
                  type="button"
                  @click="removeSpecRow(idx)"
                  class="p-2 rounded-lg text-stone-400 hover:text-red-500 hover:bg-red-50 transition-colors cursor-pointer"
                  title="删除该规格"
                >
                  <Trash2 class="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
            <button
              type="button"
              @click="addSpecRow"
              class="mt-2 flex items-center gap-1 text-[11px] font-bold text-[#ff6b35] hover:text-[#e85a24] transition-colors cursor-pointer"
            >
              <Plus class="w-3.5 h-3.5" /> 添加规格
            </button>
          </div>

          <!-- Description -->
          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">副标题描述</label>
            <input 
              type="text" 
              v-model="newDescription" 
              placeholder="e.g., 严选高品质腰果，物理慢烘，饱满易剥" 
              class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]"
            />
          </div>

          <!-- Image URL + 路径选择器 -->
          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">商品配图 URL</label>
            <div class="flex gap-2">
              <input
                type="text"
                v-model="newImage"
                placeholder="可手输直连地址，或点右侧按钮从图片库选择 /img/** 路径"
                class="flex-1 text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]"
              />
              <button
                type="button"
                @click="openImagePicker"
                class="shrink-0 px-3 py-2 flex items-center gap-1.5 rounded-xl bg-stone-100 hover:bg-stone-200 text-stone-600 text-xs font-bold cursor-pointer"
              >
                <ImagePlus class="w-3.5 h-3.5" />
                选择图片
              </button>
            </div>
            <!-- 已选配图预览 -->
            <img
              v-if="newImage"
              :src="newImage"
              alt="配图预览"
              class="mt-2 h-16 w-16 object-cover rounded-lg border border-[#e4e2de]"
            />
          </div>

          <!-- Details specifications grid -->
          <div class="border-t border-stone-100 pt-3 mt-2 grid grid-cols-2 gap-4">
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">产地</label>
              <input type="text" v-model="newOrigin" placeholder="省份/城市" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]" />
            </div>
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">保质期</label>
              <input type="text" v-model="newShelfLife" placeholder="e.g., 180天" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]" />
            </div>
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">单袋净含量</label>
              <input type="text" v-model="newWeight" placeholder="e.g., 180g" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]" />
            </div>
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">配料成分</label>
              <input type="text" v-model="newIngredients" placeholder="配料成分配方" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]" />
            </div>
          </div>
        </div>

        <!-- Buttons -->
        <div class="mt-6 flex gap-3">
          <button
            @click="closeModal"
            class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-500 font-bold text-xs cursor-pointer"
          >
            取消
          </button>
          <button
            @click="handleSaveProduct"
            class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold text-xs rounded-xl cursor-pointer"
          >
            {{ editingId ? '保存修改' : '确认上架' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 图片库路径选择弹窗：从后端本地图片库 (D:\bishe\image) 选择配图路径 -->
    <div
      v-if="showImagePicker"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      @click.self="closeImagePicker"
    >
      <div class="bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[80vh] flex flex-col">
        <!-- header -->
        <div class="flex items-center justify-between px-5 py-3.5 border-b border-stone-100">
          <div>
            <h3 class="text-sm font-bold text-stone-800">选择商品图片（本机图片库）</h3>
            <p class="text-[11px] text-stone-400 mt-0.5">点击图片即可自动填入 /img/** 路径</p>
          </div>
          <button
            @click="closeImagePicker"
            class="p-1.5 rounded-lg hover:bg-stone-100 text-stone-400 cursor-pointer"
          >
            <X class="w-4 h-4" />
          </button>
        </div>
        <!-- body -->
        <div class="p-4 overflow-y-auto flex-1">
          <div v-if="imageLoading" class="py-16 text-center text-stone-400 text-sm">正在加载图片库...</div>
          <div v-else-if="!imageList.length" class="py-16 text-center text-stone-400 text-sm">
            图片库为空，请将商品图片放入后端 D:\bishe\image 目录
          </div>
          <div v-else class="grid grid-cols-4 sm:grid-cols-5 md:grid-cols-6 gap-3">
            <button
              v-for="img in imageList"
              :key="img.url"
              type="button"
              @click="pickImage(img)"
              class="group relative aspect-square rounded-xl overflow-hidden border border-stone-200 hover:border-[#ff6b35] hover:shadow-md cursor-pointer transition-all"
              :class="{ 'border-[#ff6b35] ring-2 ring-[#ff6b35]/30': newImage === img.url }"
            >
              <img :src="img.url" :alt="img.name" class="w-full h-full object-cover" loading="lazy" />
              <span class="absolute bottom-0 inset-x-0 bg-black/55 text-white text-[9px] px-1 py-0.5 truncate">{{ img.name }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Search / Category filter panel -->
    <div class="flex flex-col sm:flex-row gap-4 justify-between items-center mb-6">
      <div class="relative w-full sm:w-80">
        <input 
          type="text" 
          v-model="searchKeyword" 
          placeholder="搜索商城已录入商品..." 
          class="w-full text-xs pl-8 pr-3 py-2.5 rounded-xl bg-[#f5f3ef] border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] text-stone-800 transition-colors"
        />
        <Search class="w-4 h-4 text-stone-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
      </div>

      <div class="flex gap-1.5 bg-[#efeeea] p-1 rounded-xl w-full sm:w-auto overflow-x-auto shrink-0">
        <button
          v-for="cat in ['全部', ...(adminStore.categories || []).map(c => c.name)]"
          :key="cat"
          @click="selectedCategory = cat"
          :class="`px-3.5 py-1.5 text-[11px] font-bold rounded-lg transition-all cursor-pointer whitespace-nowrap ${
            selectedCategory === cat
              ? 'bg-white text-stone-900 shadow-sm'
              : 'text-[#5a4136]'
          }`"
        >
          {{ cat }}
        </button>
      </div>
    </div>

    <!-- Product Table Grid -->
    <div v-if="filteredProducts.length === 0" class="text-center py-20 bg-[#fbf9f5] border border-dashed border-[#e4e2de] rounded-2xl">
      <div class="w-12 h-12 rounded-full bg-stone-100 flex items-center justify-center mx-auto mb-2 text-stone-400">
        <PackageOpen class="w-6 h-6" />
      </div>
      <p class="text-xs text-stone-500">没有录入符合筛选条件的零食</p>
    </div>

    <div v-else class="overflow-x-auto">
      <table class="w-full text-left text-xs border-collapse">
        <thead>
          <tr class="text-stone-400 border-b border-stone-100 pb-2">
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider">配图</th>
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider">商品名称</th>
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider">所属分类</th>
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider">售价 / 划线原价</th>
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider">库存及销量</th>
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider text-right">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-stone-100">
          <tr
            v-for="product in pagedProducts"
            :key="product.id"
            class="hover:bg-[#fbf9f5]/50 transition-colors"
          >
            <!-- Image -->
            <td class="py-3.5">
              <img :src="product.coverImage || product.image" :alt="product.name" class="w-11 h-11 object-cover rounded-xl border border-stone-200" />
            </td>

            <!-- Name with tags -->
            <td class="py-3.5 pr-4">
              <div class="font-bold text-stone-800 leading-tight truncate max-w-[240px]">{{ product.name }}</div>
              <p class="text-[10px] text-stone-400 mt-1 line-clamp-1 max-w-[240px]">{{ product.description }}</p>
            </td>

            <!-- Category -->
            <td class="py-3.5">
              <span class="px-2 py-0.5 bg-[#efeeea] text-[#5a4136] text-[10px] font-bold rounded-md">
                {{ product.category }}
              </span>
            </td>

            <!-- Price -->
            <td class="py-3.5">
              <span class="font-black text-stone-800">￥{{ product.price.toFixed(1) }}</span>
              <span class="text-stone-400 text-[10px] line-through block mt-0.5">￥{{ product.marketPrice.toFixed(1) }}</span>
            </td>

            <!-- Stats -->
            <td class="py-3.5 font-mono text-[11px] text-stone-600">
              <div class="flex flex-col">
                <span>剩余: <strong class="text-stone-800">{{ product.inventory }} 件</strong></span>
                <span class="text-[10px] text-stone-400 mt-0.5">已售: {{ product.sales }} 件</span>
              </div>
            </td>

            <!-- Actions -->
            <td class="py-3.5 text-right">
              <div class="flex items-center justify-end gap-1">
                <button
                  @click="handleEditProduct(product)"
                  class="p-2 text-stone-400 hover:text-[#ff6b35] rounded-xl hover:bg-[#ffdbcc]/40 cursor-pointer"
                  title="编辑商品"
                >
                  <Pencil class="w-4 h-4" />
                </button>
                <button
                  @click="handleDeleteProduct(product.id)"
                  class="p-2 text-stone-400 hover:text-red-500 rounded-xl hover:bg-red-50 cursor-pointer"
                  title="下架商品"
                >
                  <Trash2 class="w-4 h-4" />
                </button>
              </div>
            </td>

          </tr>
        </tbody>
      </table>
      <Pagination
        :total="filteredProducts.length"
        v-model:page="currentPage"
        :size="pageSize"
      />
    </div>

  </AdminLayout>
</template>
