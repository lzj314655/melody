<script setup>
import { ref, computed, onMounted } from "vue";
import { useAdminStore } from "@/store/admin";
import AdminLayout from "@/components/AdminLayout.vue";
import Pagination from "@/components/Pagination.vue";
import { Plus, Trash2, X, KeyRound, UserCog, Shield, Mail, Calendar, Eye, EyeOff } from "lucide-vue-next";

const adminStore = useAdminStore();

onMounted(async () => {
  await adminStore.loadAdmins();
});

const currentPage = ref(1);
const pageSize = 10;
const pagedAdmins = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return (adminStore.admins || []).slice(start, start + pageSize);
});

// === 新增 / 重置密码 共用模态框 ===
const showModal = ref(false);
const modalMode = ref("add"); // "add" | "reset"
const targetId = ref(null);
const formUsername = ref("");
const formEmail = ref("");
const formRole = ref("运营管理员");
const formPassword = ref("");
const showPassword = ref(false);
const formError = ref("");

const ROLES = ["超级管理员", "运营管理员"];

const openAdd = () => {
  modalMode.value = "add";
  targetId.value = null;
  formUsername.value = "";
  formEmail.value = "";
  formRole.value = "运营管理员";
  formPassword.value = "";
  formError.value = "";
  showModal.value = true;
};

const openResetPwd = (admin) => {
  modalMode.value = "reset";
  targetId.value = admin.id;
  formUsername.value = admin.username;
  formEmail.value = admin.email;
  formRole.value = admin.role;
  formPassword.value = "";
  formError.value = "";
  showModal.value = true;
};

const closeModal = () => {
  showModal.value = false;
  targetId.value = null;
  formError.value = "";
};

const handleSubmit = async () => {
  formError.value = "";
  if (modalMode.value === "add") {
    if (!formUsername.value.trim() || !formEmail.value.trim() || !formPassword.value.trim()) {
      formError.value = "请填写用户名、邮箱、密码";
      return;
    }
    try {
      await adminStore.addAdmin({
        username: formUsername.value.trim(),
        email: formEmail.value.trim(),
        role: formRole.value,
        password: formPassword.value
      });
      closeModal();
    } catch (err) {
      formError.value = err.message;
    }
  } else {
    if (!formPassword.value.trim() || formPassword.value.length < 6) {
      formError.value = "新密码至少 6 位";
      return;
    }
    try {
      await adminStore.resetAdminPassword(targetId.value, formPassword.value);
      closeModal();
    } catch (err) {
      formError.value = err.message;
    }
  }
};

const handleDelete = async (admin) => {
  if (admin.username === "admin") {
    alert("不能删除内置超级管理员");
    return;
  }
  if (!confirm(`确认删除管理员「${admin.username}」？此操作不可撤销。`)) return;
  try {
    await adminStore.deleteAdmin(admin.id);
  } catch (err) {
    alert(err.message);
  }
};
</script>

<template>
  <AdminLayout
    title="管理员账号"
    subtitle="维护后台管理账号、分配角色权限"
    :breadcrumbs="['系统管理', '管理员账号']"
  >
    <template #actions>
      <button
        @click="openAdd"
        class="text-xs bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold py-2 px-4.5 rounded-xl flex items-center gap-1 cursor-pointer shadow-sm"
      >
        <Plus class="w-4 h-4" />
        <span>新增管理员</span>
      </button>
    </template>

    <!-- 统计卡 -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-5 mb-6">
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-[#ffdbcc] text-[#ff6b35] flex items-center justify-center">
            <UserCog class="w-5 h-5" />
          </div>
          <div>
            <p class="text-[10px] text-stone-500 font-bold uppercase tracking-wider">总账号数</p>
            <p class="text-xl font-black text-stone-800 font-display">{{ adminStore.admins.length }}</p>
          </div>
        </div>
      </div>
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center">
            <Shield class="w-5 h-5" />
          </div>
          <div>
            <p class="text-[10px] text-stone-500 font-bold uppercase tracking-wider">超级管理员</p>
            <p class="text-xl font-black text-stone-800 font-display">
              {{ adminStore.admins.filter(a => a.role === '超级管理员').length }}
            </p>
          </div>
        </div>
      </div>
      <div class="bg-white border border-[#e4e2de] rounded-2xl p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
            <UserCog class="w-5 h-5" />
          </div>
          <div>
            <p class="text-[10px] text-stone-500 font-bold uppercase tracking-wider">运营管理员</p>
            <p class="text-xl font-black text-stone-800 font-display">
              {{ adminStore.admins.filter(a => a.role === '运营管理员').length }}
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- 管理员列表 -->
    <div v-if="adminStore.admins.length === 0" class="text-center py-20 bg-[#fbf9f5] border border-dashed border-[#e4e2de] rounded-2xl">
      <div class="w-12 h-12 rounded-full bg-stone-100 flex items-center justify-center mx-auto mb-2 text-stone-400">
        <UserCog class="w-6 h-6" />
      </div>
      <p class="text-xs text-stone-500">暂无管理员账号</p>
    </div>

    <div v-else class="overflow-x-auto">
      <table class="w-full text-left text-xs border-collapse">
        <thead>
          <tr class="text-stone-400 border-b border-stone-100 pb-2">
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider">账号</th>
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider">邮箱</th>
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider">角色</th>
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider">创建时间</th>
            <th class="py-3 font-bold font-mono text-[10px] uppercase tracking-wider text-right">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-stone-100">
          <tr
            v-for="admin in pagedAdmins"
            :key="admin.id"
            class="hover:bg-[#fbf9f5]/50 transition-colors"
          >
            <td class="py-3.5">
              <div class="flex items-center gap-2.5">
                <div :class="`w-9 h-9 rounded-xl flex items-center justify-center ${
                  admin.role === '超级管理员'
                    ? 'bg-purple-100 text-purple-600'
                    : 'bg-emerald-50 text-emerald-600'
                }`">
                  <Shield v-if="admin.role === '超级管理员'" class="w-4 h-4" />
                  <UserCog v-else class="w-4 h-4" />
                </div>
                <span class="font-bold text-stone-800">{{ admin.username }}</span>
                <span v-if="admin.username === 'admin'" class="text-[9px] font-mono text-stone-400 bg-stone-100 px-1.5 py-0.5 rounded">内置</span>
              </div>
            </td>
            <td class="py-3.5 text-stone-600 font-mono text-[11px]">
              <div class="flex items-center gap-1.5">
                <Mail class="w-3 h-3 text-stone-400" />
                <span>{{ admin.email }}</span>
              </div>
            </td>
            <td class="py-3.5">
              <span :class="`px-2 py-0.5 rounded-md font-bold text-[10px] ${
                admin.role === '超级管理员'
                  ? 'bg-purple-50 text-purple-600'
                  : 'bg-emerald-50 text-emerald-600'
              }`">
                {{ admin.role }}
              </span>
            </td>
            <td class="py-3.5 text-stone-500 font-mono text-[11px]">
              <div class="flex items-center gap-1.5">
                <Calendar class="w-3 h-3 text-stone-400" />
                <span>{{ admin.createdAt }}</span>
              </div>
            </td>
            <td class="py-3.5 text-right">
              <div class="flex items-center justify-end gap-1">
                <button
                  @click="openResetPwd(admin)"
                  class="text-[11px] font-semibold text-[#5a4136] hover:text-[#ff6b35] flex items-center gap-1 px-2 py-1 rounded-lg hover:bg-[#ffdbcc]/40 cursor-pointer"
                  title="重置密码"
                >
                  <KeyRound class="w-3 h-3" />
                  <span>重置密码</span>
                </button>
                <button
                  @click="handleDelete(admin)"
                  :disabled="admin.username === 'admin'"
                  :class="`p-2 rounded-xl cursor-pointer ${
                    admin.username === 'admin'
                      ? 'text-stone-300 cursor-not-allowed'
                      : 'text-stone-400 hover:text-red-500 hover:bg-red-50'
                  }`"
                  :title="admin.username === 'admin' ? '内置账号不可删除' : '删除'"
                >
                  <Trash2 class="w-4 h-4" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <Pagination
        :total="(adminStore.admins || []).length"
        v-model:page="currentPage"
        :size="pageSize"
      />
    </div>

    <!-- 模态框（新增 / 重置密码） -->
    <div
      v-if="showModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
    >
      <div class="bg-white rounded-3xl border border-[#e4e2de] p-6 max-w-md w-full shadow-2xl">
        <div class="flex items-center justify-between border-b border-stone-100 pb-3 mb-4">
          <h3 class="text-sm font-bold text-stone-800 font-display">
            {{ modalMode === 'add' ? '新增管理员' : '重置密码' }}
          </h3>
          <button @click="closeModal" class="p-1 rounded-full text-stone-400 hover:bg-stone-100">
            <X class="w-4 h-4" />
          </button>
        </div>

        <div v-if="modalMode === 'reset'" class="mb-3 text-[11px] text-stone-500 font-mono bg-[#efeeea] px-3 py-2 rounded-lg">
          正在为 <strong class="text-stone-800">{{ formUsername }}</strong> ({{ formEmail }}) 重置密码
        </div>

        <div v-if="modalMode === 'add'" class="space-y-3">
          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">用户名 *</label>
            <input v-model="formUsername" type="text" placeholder="e.g., operator_zhang" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]" />
          </div>
          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">邮箱 *</label>
            <input v-model="formEmail" type="email" placeholder="e.g., zhang@melody.com" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]" />
          </div>
          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">角色 *</label>
            <select v-model="formRole" class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]">
              <option v-for="r in ROLES.filter(x => x !== '超级管理员')" :key="r" :value="r">{{ r }}</option>
            </select>
          </div>
        </div>

        <div class="mt-3">
          <label class="block text-[11px] font-bold text-stone-500 mb-1">
            {{ modalMode === 'add' ? '初始密码 *' : '新密码 *' }}
          </label>
          <div class="relative">
            <input
              v-model="formPassword"
              :type="showPassword ? 'text' : 'password'"
              :placeholder="modalMode === 'add' ? '至少 6 位' : '输入新密码，至少 6 位'"
              class="w-full text-xs px-3.5 py-2 pr-9 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] font-mono"
            />
            <button
              type="button"
              @click="showPassword = !showPassword"
              class="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-stone-400 hover:text-stone-700 cursor-pointer"
            >
              <Eye v-if="!showPassword" class="w-3.5 h-3.5" />
              <EyeOff v-else class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <div v-if="formError" class="mt-3 text-[11px] text-red-600 bg-red-50 px-3 py-2 rounded-lg">
          {{ formError }}
        </div>

        <div class="mt-6 flex gap-3">
          <button
            @click="closeModal"
            class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-500 font-bold text-xs cursor-pointer"
          >
            取消
          </button>
          <button
            @click="handleSubmit"
            class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold text-xs rounded-xl cursor-pointer"
          >
            {{ modalMode === 'add' ? '确认新增' : '确认重置' }}
          </button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>
