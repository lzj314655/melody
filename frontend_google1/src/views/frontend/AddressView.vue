<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import FrontendLayout from "@/components/FrontendLayout.vue";
import { ArrowLeft, MapPin, Plus, Trash2, Edit2, ShieldAlert } from "lucide-vue-next";

const router = useRouter();
const userStore = useUserStore();

// Modal form states
const showModal = ref(false);
const isEditing = ref(false);
const editingAddressId = ref(null);

const formName = ref("");
const formPhone = ref("");
const formProvince = ref("上海市");
const formCity = ref("上海市");
const formDistrict = ref("浦东新区");
const formDetail = ref("");
const formTag = ref("家");
const formIsDefault = ref(false);

onMounted(async () => {
  await userStore.loadUser().catch(() => {});
  await userStore.loadAddresses().catch(() => {});
});

const openAddModal = () => {
  isEditing.value = false;
  editingAddressId.value = null;
  formName.value = "";
  formPhone.value = "";
  formProvince.value = "上海市";
  formCity.value = "上海市";
  formDistrict.value = "浦东新区";
  formDetail.value = "";
  formTag.value = "家";
  formIsDefault.value = false;
  showModal.value = true;
};

const openEditModal = (addr) => {
  isEditing.value = true;
  editingAddressId.value = addr.id;
  formName.value = addr.consignee || addr.name || '';
  formPhone.value = addr.phone || '';
  formProvince.value = addr.province || '上海市';
  formCity.value = addr.city || '上海市';
  formDistrict.value = addr.district || '浦东新区';
  formDetail.value = addr.detail || '';
  formTag.value = addr.tag || '家';
  formIsDefault.value = !!(addr.isDefault ?? (addr.tag === 'DEFAULT'));
  showModal.value = true;
};

const handleSaveAddress = async () => {
  if (!formName.value || !formPhone.value || !formDetail.value) {
    alert("请完整填写收货人、联系电话及详细收货地址！");
    return;
  }

  const payload = {
    name: formName.value,
    consignee: formName.value,
    phone: formPhone.value,
    province: formProvince.value,
    city: formCity.value,
    district: formDistrict.value,
    detail: formDetail.value,
    tag: formTag.value,
    isDefault: formIsDefault.value ? 1 : 0,
  };

  try {
    if (isEditing.value) {
      await userStore.updateAddress({ ...payload, id: editingAddressId.value });
      alert("收货地址更新成功！");
    } else {
      await userStore.addAddress(payload);
      alert("收货地址保存成功！");
    }
    showModal.value = false;
  } catch (err) {
    alert("保存失败: " + err.message);
  }
};

const handleDelete = async (id) => {
  if (confirm("确定要删除此收货地址吗？")) {
    await userStore.deleteAddress(id);
  }
};

const handleSetDefault = async (addr) => {
  try {
    await userStore.updateAddress({ ...addr, isDefault: 1 });
    alert("已成功设为默认地址");
  } catch (err) {
    alert("设为默认失败: " + (err?.message || "未知错误"));
  }
};
</script>

<template>
  <FrontendLayout>
    <div class="max-w-[1080px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
      
      <!-- Back Header -->
      <div class="flex items-center justify-between mb-8">
        <div class="flex items-center gap-3">
          <button 
            @click="router.push('/profile')" 
            class="p-2 bg-white border border-[#e4e2de] hover:bg-[#efeeea] rounded-xl text-stone-600 transition-colors cursor-pointer"
          >
            <ArrowLeft class="w-4 h-4" />
          </button>
          <div>
            <h1 class="text-xl font-bold tracking-tight text-stone-800 font-display flex items-center gap-2">
              <MapPin class="w-5 h-5 text-[#ff6b35]" />
              <span>收货地址管理</span>
            </h1>
            <p class="text-xs text-stone-500 mt-0.5">
              维护您的常用收货人及发货地址，以便快速结算下单。
            </p>
          </div>
        </div>

        <button 
          @click="openAddModal"
          class="text-xs bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold py-2.5 px-4 rounded-xl flex items-center gap-1 cursor-pointer shadow-sm active:scale-95 transition-all"
        >
          <Plus class="w-4 h-4" />
          <span>新增收货地址</span>
        </button>
      </div>

      <!-- Empty State -->
      <div v-if="userStore.addresses.length === 0" class="bg-white border border-[#e4e2de] rounded-3xl p-16 text-center shadow-sm">
        <MapPin class="w-12 h-12 text-stone-300 mx-auto mb-3" />
        <h3 class="text-sm font-bold text-stone-700">您还没有添加任何收货地址哦</h3>
        <p class="text-xs text-stone-400 mt-1">建议您现在新增一个，省去下单时的反复录入麻烦</p>
        <button 
          @click="openAddModal" 
          class="mt-4 px-4 py-2 bg-[#ff6b35] text-white font-bold rounded-xl text-xs hover:bg-[#ff6b35]/90 cursor-pointer shadow-sm"
        >
          立即创建首个地址
        </button>
      </div>

      <!-- Address Grid -->
      <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div
          v-for="addr in userStore.addresses"
          :key="addr.id"
          class="p-5 rounded-3xl border border-[#e4e2de] hover:border-[#ff6b35]/30 hover:shadow-md transition-all bg-white flex flex-col justify-between"
        >
          <div>
            <div class="flex items-center justify-between mb-3">
              <div class="flex items-center gap-2">
                <span class="text-xs font-bold text-stone-800">{{ addr.consignee || addr.name }}</span>
                <span 
                  v-if="addr.isDefault" 
                  class="text-[9px] font-bold text-[#ff6b35] bg-[#ffdbcc] px-2 py-0.5 rounded-md"
                >
                  默认
                </span>
              </div>
              <span class="text-[10px] px-2.5 py-0.5 bg-[#efeeea] text-[#5a4136] rounded-md font-bold font-mono">{{ addr.tag }}</span>
            </div>
            
            <p class="text-xs font-semibold text-stone-600 font-mono">{{ addr.phone }}</p>
            <p class="text-[11px] text-stone-400 mt-2.5 leading-relaxed">
              {{ addr.province }} {{ addr.city }} {{ addr.district }} <br/>
              <span class="text-stone-700 font-medium">{{ addr.detail }}</span>
            </p>
          </div>

          <!-- Actions Footer -->
          <div class="mt-6 pt-3 border-t border-stone-100 flex justify-between items-center text-xs">
            <button
              v-if="!addr.isDefault"
              @click="handleSetDefault(addr)"
              class="text-[11px] text-stone-500 hover:text-[#ff6b35] font-bold cursor-pointer"
            >
              设为默认地址
            </button>
            <span v-else class="text-[10px] text-teal-600 font-bold flex items-center gap-0.5">
              <span class="material-symbols-outlined text-sm">verified</span>
              <span>主用收货地址</span>
            </span>

            <div class="flex items-center gap-1.5">
              <button 
                @click="openEditModal(addr)"
                class="p-1.5 text-stone-400 hover:text-stone-800 hover:bg-[#fbf9f5] rounded-lg transition-colors cursor-pointer"
                title="编辑地址"
              >
                <Edit2 class="w-3.5 h-3.5" />
              </button>
              
              <button 
                @click="handleDelete(addr.id)"
                class="p-1.5 text-stone-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors cursor-pointer"
                title="删除地址"
              >
                <Trash2 class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Add/Edit Modal popup overlay -->
      <div 
        v-if="showModal" 
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      >
        <div class="bg-white rounded-3xl border border-[#e4e2de] p-6 max-w-md w-full shadow-2xl animate-fade-in">
          <h3 class="text-sm font-bold text-stone-800 mb-4 font-display flex items-center gap-1.5 border-b border-stone-100 pb-3">
            <Plus class="w-4.5 h-4.5 text-[#ff6b35]" />
            <span>{{ isEditing ? "修改收货地址" : "新增收货地址" }}</span>
          </h3>

          <div class="space-y-4">
            <!-- Name -->
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">收货人姓名 *</label>
              <input
                type="text"
                v-model="formName"
                placeholder="请输入姓名"
                class="w-full text-xs px-3.5 py-2.5 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]"
              />
            </div>

            <!-- Phone -->
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">手机号码 *</label>
              <input
                type="text"
                v-model="formPhone"
                placeholder="请输入收货人手机号"
                class="w-full text-xs px-3.5 py-2.5 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]"
              />
            </div>

            <!-- Area Grid -->
            <div class="grid grid-cols-3 gap-3">
              <div>
                <label class="block text-[11px] font-bold text-stone-500 mb-1">省份</label>
                <input
                  type="text"
                  v-model="formProvince"
                  class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de]"
                />
              </div>
              <div>
                <label class="block text-[11px] font-bold text-stone-500 mb-1">城市</label>
                <input
                  type="text"
                  v-model="formCity"
                  class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de]"
                />
              </div>
              <div>
                <label class="block text-[11px] font-bold text-stone-500 mb-1">区县</label>
                <input
                  type="text"
                  v-model="formDistrict"
                  class="w-full text-xs px-3 py-2 rounded-xl border border-[#e4e2de]"
                />
              </div>
            </div>

            <!-- Detail -->
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">详细地址 *</label>
              <input
                type="text"
                v-model="formDetail"
                placeholder="街区、路名、门牌等详细地址"
                class="w-full text-xs px-3.5 py-2.5 rounded-xl border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]"
              />
            </div>

            <!-- Tags selector -->
            <div class="flex items-center gap-3">
              <label class="text-[11px] font-bold text-stone-500">标签:</label>
              <div class="flex gap-1.5">
                <button 
                  v-for="tag in ['家', '公司', '学校', '其他']" 
                  :key="tag"
                  type="button"
                  @click="formTag = tag"
                  :class="`px-3 py-1 text-[11px] rounded-lg font-bold border ${
                    formTag === tag 
                      ? 'bg-[#ff6b35] text-white border-transparent' 
                      : 'border-[#e4e2de] text-[#5a4136]'
                  }`"
                >
                  {{ tag }}
                </button>
              </div>
            </div>

            <!-- Default Checkbox -->
            <div class="flex items-center gap-2">
              <input type="checkbox" id="chk-def-modal" v-model="formIsDefault" class="w-3.5 h-3.5 accent-[#ff6b35]" />
              <label for="chk-def-modal" class="text-xs text-stone-600 font-medium">设为默认收货地址</label>
            </div>
          </div>

          <!-- Buttons -->
          <div class="mt-6 flex gap-3">
            <button 
              type="button"
              @click="showModal = false"
              class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-500 font-bold text-xs cursor-pointer"
            >
              取消
            </button>
            <button 
              type="button"
              @click="handleSaveAddress"
              class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold text-xs rounded-xl cursor-pointer"
            >
              保存并应用
            </button>
          </div>
        </div>
      </div>

    </div>
  </FrontendLayout>
</template>

<style scoped>
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.animate-fade-in {
  animation: fadeIn 0.2s ease-out forwards;
}
</style>
