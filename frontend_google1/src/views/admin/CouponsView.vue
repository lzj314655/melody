<script setup>
import { ref, computed, onMounted } from "vue";
import { useAdminStore } from "@/store/admin";
import { couponFace } from "@/utils/coupon";
import AdminLayout from "@/components/AdminLayout.vue";
import Pagination from "@/components/Pagination.vue";
import { Plus, Trash2, Megaphone, Calendar, AlertCircle, RefreshCw, PauseCircle, PlayCircle } from "lucide-vue-next";


const adminStore = useAdminStore();
const currentPage = ref(1);
const pageSize = 8;

const pagedCampaigns = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return (adminStore.campaigns || []).slice(start, start + pageSize);
});

const showAddModal = ref(false);
const newTitle = ref("");
const newDiscount = ref(10);
const newMinSpend = ref(50);
const newDescription = ref("");
const newExpiryDate = ref(new Date(Date.now() + 90 * 24 * 3600 * 1000).toISOString().slice(0, 10));
const newTotal = ref(100);

const formatDate = (s) => {
  if (!s) return "";
  try {
    let d;
    if (Array.isArray(s)) {
      const [y, m, day] = s;
      d = new Date(y, m - 1, day);
    } else {
      d = new Date(s);
    }
    if (isNaN(d.getTime())) return String(s);
    const pad = (n) => n.toString().padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  } catch { return String(s); }
};

onMounted(async () => {
  await adminStore.loadAdminData();
});

// 刷新按钮状态：旋转动画 + 完成后短暂显示"已更新"
const refreshing = ref(false);
const justRefreshed = ref(false);
let refreshTimer = null;
const handleRefresh = async () => {
  if (refreshing.value) return;
  refreshing.value = true;
  justRefreshed.value = false;
  try {
    await adminStore.loadAdminData();
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

const handleAddCampaign = async () => {
  if (!newTitle.value || newDiscount.value <= 0 || newMinSpend.value <= 0) {
    alert("请正确填写优惠券标题、折扣和满减金额门槛！");
    return;
  }
  const endDate = new Date(newExpiryDate.value);
  const payload = {
    name: newTitle.value,
    type: "满减",
    discount: parseFloat(newDiscount.value),
    minSpend: parseFloat(newMinSpend.value),
    description: newDescription.value || `满 ${newMinSpend.value} 减 ${newDiscount.value} 专享抵用券`,
    total: parseInt(newTotal.value || 100),
    remaining: parseInt(newTotal.value || 100),
    startDate: new Date().toISOString().slice(0, 19).replace("T", " "),
    endDate: endDate.toISOString().slice(0, 19).replace("T", " "),
    status: 1,
  };

  try {
    await adminStore.addCampaign(payload);
    showAddModal.value = false;
    alert("全新优惠券企划发布成功！");
    newTitle.value = "";
    newDiscount.value = 10;
    newMinSpend.value = 50;
    newDescription.value = "";
    newTotal.value = 100;
  } catch (err) {
    alert(err.message || "发布失败");
  }
};

const handleToggleCampaign = async (campaign) => {
  const isActive = campaign.status === 1;
  const next = isActive ? 0 : 1;
  try {
    await adminStore.updateCampaign(campaign.id, { status: next });
    alert(isActive ? "该券已停用" : "该券已重新启用");
  } catch (e) {
    alert(e.message || "操作失败");
  }
};

const handleDeleteCoupon = async (id) => {
  if (!confirm("确认永久删除该张优惠券吗？该券所有用户领取记录也会一并清除，操作不可撤销。")) return;
  try {
    await adminStore.deleteCoupon(id);
    alert("优惠券已删除。");
  } catch (e) {
    alert(e.message || "删除失败");
  }
};
</script>

<template>
  <AdminLayout
    title="营销管理"
    subtitle="发布满减现金优惠券促销企划，吸引吃货购买松子等特惠食品"
    :breadcrumbs="['营销管理']"
  >
    <template #actions>
      <button
        @click="handleRefresh"
        :disabled="refreshing"
        class="text-xs bg-[#efeeea] hover:bg-stone-200 text-[#5a4136] font-bold py-2 px-3.5 rounded-xl flex items-center gap-1 cursor-pointer disabled:opacity-60"
      >
        <RefreshCw :class="refreshing ? 'animate-spin' : ''" class="w-3.5 h-3.5" />
        <span>{{ refreshing ? '刷新中…' : (justRefreshed ? '已更新 ✓' : '刷新') }}</span>
      </button>
      <button
        @click="showAddModal = true"
        class="ml-2 text-xs bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold py-2 px-4.5 rounded-xl flex items-center gap-1 cursor-pointer shadow-sm"
      >
        <Plus class="w-4 h-4" />
        <span>新增满减券活动</span>
      </button>
    </template>

    <div
      v-if="showAddModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
    >
      <div class="bg-white rounded-3xl border border-[#e4e2de] p-6 max-w-md w-full shadow-2xl animate-scale-up">
        <h3 class="text-sm font-bold text-stone-800 font-display mb-4 flex items-center gap-1.5 border-b border-stone-100 pb-3">
          <Plus class="w-4.5 h-4.5 text-[#ff6b35]" />
          <span>创建新满减券企划</span>
        </h3>

        <div class="space-y-4">
          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">优惠券标题 *</label>
            <input
              type="text"
              v-model="newTitle"
              placeholder="e.g., 暑期冰镇点心专享券"
              class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]"
            />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">抵扣面额 (元) *</label>
              <input type="number" v-model="newDiscount" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]" />
            </div>
            <div>
              <label class="block text-[11px] font-bold text-stone-500 mb-1">使用门槛 (元) *</label>
              <input type="number" v-model="newMinSpend" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]" />
            </div>
          </div>

          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">发行总量</label>
            <input type="number" v-model="newTotal" class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]" />
          </div>

          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">活动宣传副标题</label>
            <input
              type="text"
              v-model="newDescription"
              placeholder="e.g., 全场糕点蜜饯类通用"
              class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de]"
            />
          </div>

          <div>
            <label class="block text-[11px] font-bold text-stone-500 mb-1">有效截止期 *</label>
            <input
              type="date"
              v-model="newExpiryDate"
              class="w-full text-xs px-3.5 py-2 rounded-xl border border-[#e4e2de] font-mono"
            />
          </div>
        </div>

        <div class="mt-6 flex gap-3">
          <button @click="showAddModal = false" class="flex-1 py-2.5 border border-[#e4e2de] rounded-xl text-stone-500 font-bold text-xs cursor-pointer">取消</button>
          <button @click="handleAddCampaign" class="flex-1 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold text-xs rounded-xl cursor-pointer">发布生效</button>
        </div>
      </div>
    </div>

    <div v-if="adminStore.campaigns.length === 0" class="text-center py-20 bg-[#fbf9f5] border border-dashed border-[#e4e2de] rounded-2xl">
      <div class="w-12 h-12 rounded-full bg-stone-100 flex items-center justify-center mx-auto mb-2 text-stone-400">
        <Megaphone class="w-6 h-6" />
      </div>
      <p class="text-xs text-stone-500">目前没有正在运行的优惠券企划</p>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div
        v-for="campaign in pagedCampaigns"
        :key="campaign.id"
        :class="`border border-[#e4e2de] rounded-2xl overflow-hidden bg-white hover:shadow-sm transition-shadow flex relative group ${
          campaign.status === 0 ? 'opacity-60' : ''
        }`"
      >
        <div class="bg-gradient-to-br from-[#ff6b35] to-[#ffdbcc] text-white p-6 flex flex-col justify-center items-center shrink-0 min-w-[110px] text-center">
          <span class="text-xs uppercase tracking-wider font-bold opacity-85">抵用面值</span>
          <span class="text-3xl font-black font-display mt-1 leading-none">
            <template v-for="(cf, i) in [couponFace(campaign.type, campaign.discount)]" :key="i">
              {{ cf.prefix }}{{ cf.value }}<span class="text-lg">{{ cf.suffix }}</span>
            </template>
          </span>
          <span class="text-[10px] font-semibold mt-2 opacity-90">满 ￥{{ campaign.minSpend }} 可用</span>
          <div class="w-full border-t border-dashed border-white/30 mt-2 pt-1.5">
            <span class="text-[9px] font-mono">剩余 {{ campaign.remaining }}/{{ campaign.total }}</span>
          </div>
        </div>

        <div class="p-5 flex-1 flex flex-col justify-between">
          <div>
            <div class="flex items-center justify-between gap-2 mb-1.5">
              <h4 class="text-xs font-bold text-stone-800 leading-tight truncate max-w-[200px]">{{ campaign.name }}</h4>
                <span :class="`px-2 py-0.5 text-[9px] font-bold rounded-md font-mono ${
                  campaign.status === 1 ? 'bg-green-50 text-green-600' : 'bg-stone-100 text-stone-500'
                }`">
                  {{ campaign.status === 1 ? '进行中' : '已停用' }}
                </span>
            </div>
            <p class="text-[11px] text-stone-500 leading-relaxed">{{ campaign.description }}</p>
          </div>

          <div class="flex justify-between items-center border-t border-stone-100 pt-3 mt-4">
            <div class="flex items-center gap-1 text-[10px] text-stone-400 font-mono">
              <Calendar class="w-3.5 h-3.5 shrink-0" />
              <span>至: {{ formatDate(campaign.endDate) }}</span>
            </div>

            <div class="flex gap-1">
              <button
                @click="handleToggleCampaign(campaign)"
                :class="`p-1.5 rounded-lg cursor-pointer ${
                  campaign.status === 1 ? 'text-stone-400 hover:text-amber-500 hover:bg-amber-50' : 'text-emerald-500 hover:bg-emerald-50'
                }`"
                :title="campaign.status === 1 ? '停用' : '启用'"
              >
                <PauseCircle v-if="campaign.status === 1" class="w-4 h-4" />
                <PlayCircle v-else class="w-4 h-4" />
              </button>
              <button
                @click="handleDeleteCoupon(campaign.id)"
                class="p-1.5 text-stone-400 hover:text-red-500 hover:bg-red-50 rounded-lg cursor-pointer"
                title="删除该券"
              >
                <Trash2 class="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

      </div>
      <Pagination
        :total="(adminStore.campaigns || []).length"
        v-model:page="currentPage"
        :size="pageSize"
      />
    </div>

  </AdminLayout>
</template>