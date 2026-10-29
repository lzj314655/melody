<script setup>
import { ref, computed, onMounted } from "vue";
import { useAdminStore } from "@/store/admin";
import AdminLayout from "@/components/AdminLayout.vue";
import { Plus, Pencil, Trash2, X, FolderTree, CornerDownRight, PackageOpen } from "lucide-vue-next";

const adminStore = useAdminStore();

onMounted(async () => {
  await adminStore.loadCategories();
});

// === 树形结构组装：扁平 parentId → 嵌套 children ===
const categoryTree = computed(() => {
  const flat = adminStore.categories;
  const map = new Map();
  flat.forEach(c => map.set(c.id, { ...c, children: [] }));
  const roots = [];
  map.forEach(node => {
    if (node.parentId && map.has(node.parentId)) {
      map.get(node.parentId).children.push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
});

// 是否有子级（用于「删除」按钮的禁用）
const hasChildren = (id) => adminStore.categories.some(c => c.parentId === id);

// === 模态框状态 ===
const showModal = ref(false);
const editingId = ref(null);    // null = 新增模式
const parentId = ref(null);     // null = 顶级，否则为父级 id
const formName = ref("");

const openAddRoot = () => {
  editingId.value = null;
  parentId.value = null;
  formName.value = "";
  showModal.value = true;
};

const openAddChild = (parent) => {
  editingId.value = null;
  parentId.value = parent.id;
  formName.value = "";
  showModal.value = true;
};

const openEdit = (cat) => {
  editingId.value = cat.id;
  parentId.value = cat.parentId;
  formName.value = cat.name;
  showModal.value = true;
};

const closeModal = () => {
  showModal.value = false;
  editingId.value = null;
  parentId.value = null;
  formName.value = "";
};

const handleSave = async () => {
  if (!formName.value.trim()) {
    alert("请输入分类名称");
    return;
  }
  try {
    if (editingId.value) {
      await adminStore.updateCategory(editingId.value, { name: formName.value.trim() });
    } else {
      await adminStore.addCategory({
        name: formName.value.trim(),
        parentId: parentId.value
      });
    }
    closeModal();
  } catch (err) {
    alert(err.message);
  }
};

const handleDelete = async (cat) => {
  if (hasChildren(cat.id)) {
    alert("存在子级分类，请先删除子级");
    return;
  }
  if (!confirm(`确认删除分类「${cat.name}」？此操作不可撤销。`)) return;
  try {
    await adminStore.deleteCategory(cat.id);
  } catch (err) {
    alert(err.message);
  }
};

const parentName = (id) => {
  if (!id) return null;
  return adminStore.categories.find(c => c.id === id)?.name;
};
</script>

<template>
  <AdminLayout
    title="分类管理"
    subtitle="维护麦乐迪零食商城的商品分类树，支持多级嵌套"
    :breadcrumbs="['商品管理', '分类管理']"
  >
    <template #actions>
      <button
        @click="openAddRoot"
        class="text-xs bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold py-2 px-4.5 rounded-xl flex items-center gap-1 cursor-pointer shadow-sm"
      >
        <Plus class="w-4 h-4" />
        <span>新增顶级分类</span>
      </button>
    </template>

    <!-- 空态 -->
    <div v-if="categoryTree.length === 0" class="text-center py-20 bg-[#fbf9f5] border border-dashed border-[#e4e2de] rounded-2xl">
      <div class="w-12 h-12 rounded-full bg-stone-100 flex items-center justify-center mx-auto mb-2 text-stone-400">
        <FolderTree class="w-6 h-6" />
      </div>
      <p class="text-xs text-stone-500">暂无分类，点击右上角新增</p>
    </div>

    <!-- 树形分类列表 -->
    <div v-else class="space-y-1.5">
      <template v-for="root in categoryTree" :key="root.id">
        <!-- 顶级分类行 -->
        <div class="flex items-center gap-3 bg-white border border-[#e4e2de] rounded-xl px-4 py-3 hover:border-[#ff6b35]/30 transition-colors group">
          <FolderTree class="w-4 h-4 text-[#ff6b35] shrink-0" />
          <span class="text-sm font-bold text-stone-800 flex-1">{{ root.name }}</span>
          <span class="text-[10px] font-mono text-stone-400 bg-[#efeeea] px-2 py-0.5 rounded">
            {{ root.children.length }} 个子级
          </span>
          <button
            @click="openAddChild(root)"
            class="text-[11px] text-[#5a4136] hover:text-[#ff6b35] font-semibold flex items-center gap-1 px-2 py-1 rounded-lg hover:bg-[#ffdbcc]/40"
            title="新增子分类"
          >
            <CornerDownRight class="w-3 h-3" />
            <span>新增子级</span>
          </button>
          <button
            @click="openEdit(root)"
            class="p-1.5 text-stone-400 hover:text-[#ff6b35] rounded-lg hover:bg-[#ffdbcc]/40"
            title="编辑"
          >
            <Pencil class="w-3.5 h-3.5" />
          </button>
          <button
            @click="handleDelete(root)"
            :disabled="hasChildren(root.id)"
            :class="`p-1.5 rounded-lg cursor-pointer ${
              hasChildren(root.id)
                ? 'text-stone-300 cursor-not-allowed'
                : 'text-stone-400 hover:text-red-500 hover:bg-red-50'
            }`"
            :title="hasChildren(root.id) ? '存在子级，无法删除' : '删除'"
          >
            <Trash2 class="w-3.5 h-3.5" />
          </button>
        </div>

        <!-- 子级分类行（递归展示） -->
        <div
          v-for="child in root.children"
          :key="child.id"
          class="flex items-center gap-3 bg-[#fbf9f5] border border-[#e4e2de] rounded-xl px-4 py-2.5 ml-8 hover:border-[#ff6b35]/30 transition-colors"
        >
          <CornerDownRight class="w-3.5 h-3.5 text-stone-400 shrink-0" />
          <span class="text-xs font-medium text-stone-700 flex-1">{{ child.name }}</span>
          <span class="text-[10px] font-mono text-stone-400">id: {{ child.id }}</span>
          <button
            @click="openEdit(child)"
            class="p-1.5 text-stone-400 hover:text-[#ff6b35] rounded-lg hover:bg-[#ffdbcc]/40 cursor-pointer"
            title="编辑"
          >
            <Pencil class="w-3.5 h-3.5" />
          </button>
          <button
            @click="handleDelete(child)"
            class="p-1.5 text-stone-400 hover:text-red-500 rounded-lg hover:bg-red-50 cursor-pointer"
            title="删除"
          >
            <Trash2 class="w-3.5 h-3.5" />
          </button>
        </div>
      </template>
    </div>

    <!-- 模态框 -->
    <div
      v-if="showModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
    >
      <div class="bg-white rounded-3xl border border-[#e4e2de] p-6 max-w-md w-full shadow-2xl">
        <div class="flex items-center justify-between border-b border-stone-100 pb-3 mb-4">
          <h3 class="text-sm font-bold text-stone-800 font-display">
            {{ editingId ? '编辑分类' : (parentId ? '新增子分类' : '新增顶级分类') }}
          </h3>
          <button @click="closeModal" class="p-1 rounded-full text-stone-400 hover:bg-stone-100">
            <X class="w-4 h-4" />
          </button>
        </div>

        <div v-if="parentId && !editingId" class="mb-3 text-[11px] text-stone-500 font-mono bg-[#efeeea] px-3 py-2 rounded-lg">
          所属父级：<strong class="text-stone-800">{{ parentName(parentId) }}</strong>
        </div>

        <label class="block text-[11px] font-bold text-stone-500 mb-1">分类名称 *</label>
        <input
          type="text"
          v-model="formName"
          :placeholder="parentId ? 'e.g., 夏威夷果' : 'e.g., 海鲜零食'"
          class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]"
        />

        <div class="mt-6 flex gap-3">
          <button
            @click="closeModal"
            class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-500 font-bold text-xs cursor-pointer"
          >
            取消
          </button>
          <button
            @click="handleSave"
            class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold text-xs rounded-xl cursor-pointer"
          >
            {{ editingId ? '保存' : '确认新增' }}
          </button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>
