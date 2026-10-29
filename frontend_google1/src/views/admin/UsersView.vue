<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useAdminStore } from "@/store/admin";
import AdminLayout from "@/components/AdminLayout.vue";
import Pagination from "@/components/Pagination.vue";
import { Search, Gift, RefreshCw, Mail, Phone, UserX, UserCheck } from "lucide-vue-next";

const adminStore = useAdminStore();

const searchKeyword = ref("");
const selectedRank = ref("全部");
const currentPage = ref(1);
const pageSize = 12;

const ranks = ["全部", "普通会员", "银牌会员", "金牌会员", "钻石会员"];

const formatDate = (s) => {
  if (!s) return "—";
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
  await adminStore.loadUsers();
});

// 刷新重置按钮状态：清空筛选并重新拉取，带旋转动画 + 完成后短暂显示"已更新"
const refreshing = ref(false);
const justRefreshed = ref(false);
let refreshTimer = null;
const handleRefresh = async () => {
  if (refreshing.value) return;
  refreshing.value = true;
  justRefreshed.value = false;
  try {
    searchKeyword.value = "";
    selectedRank.value = "全部";
    currentPage.value = 1;
    await adminStore.loadUsers();
    justRefreshed.value = true;
    if (refreshTimer) clearTimeout(refreshTimer);
    refreshTimer = setTimeout(() => { justRefreshed.value = false; }, 1500);
  } catch (err) {
    alert("刷新失败：" + (err.message || err));
  } finally {
    refreshing.value = false;
  }
};

const filteredCustomers = computed(() => {
  let list = adminStore.users || [];
  if (selectedRank.value !== "全部") {
    list = list.filter(c => c.rank === selectedRank.value);
  }
  const kw = searchKeyword.value.trim().toLowerCase();
  if (kw) {
    list = list.filter(c =>
      (c.nickname || "").toLowerCase().includes(kw) ||
      (c.username || "").toLowerCase().includes(kw) ||
      (c.phone || "").includes(kw) ||
      String(c.id).includes(kw)
    );
  }
  return list;
});

const pagedCustomers = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredCustomers.value.slice(start, start + pageSize);
});
watch([searchKeyword, selectedRank], () => { currentPage.value = 1; });

const handleSearch = async () => {
  await adminStore.loadUsers(searchKeyword.value, selectedRank.value);
};

const handleGiftPoints = async (customer) => {
  const amountStr = prompt(`请输入赠送给用户「${customer.nickname}」的会员积分数：`, "100");
  const amount = parseInt(amountStr);
  if (isNaN(amount) || amount <= 0) return;
  try {
    await adminStore.giftPoints(customer.id, amount);
    alert(`赠送成功！已向该用户发放 ${amount} 积分。`);
    await adminStore.loadUsers(searchKeyword.value, selectedRank.value);
  } catch (e) {
    alert("赠送失败：" + e.message);
  }
};

const handleToggleStatus = async (customer) => {
  const isFrozen = customer.status === 0;
  if (!isFrozen) {
    if (!confirm(`确定要锁定用户「${customer.nickname}」吗？锁定后该用户将无法登录前台下单。`)) return;
  }
  try {
    await adminStore.updateUserStatus(customer.id, isFrozen ? 1 : 0);
    alert(isFrozen ? "已解锁该用户" : "用户已锁定");
    await adminStore.loadUsers(searchKeyword.value, selectedRank.value);
  } catch (e) {
    alert("操作失败：" + e.message);
  }
};

const rankClass = (rank) => ({
  "钻石会员": "bg-purple-100 text-purple-700",
  "金牌会员": "bg-amber-100 text-amber-700",
  "银牌会员": "bg-blue-100 text-blue-700",
}[rank] || "bg-stone-100 text-stone-600");
</script>

<template>
  <AdminLayout
    title="用户管理"
    subtitle="管理注册会员的基础资料、会员等级、消费积分、账户余额及安全状态控制"
    :breadcrumbs="['首页', '用户管理']"
  >
    <template #actions>
      <button
        class="text-xs bg-white border border-[#e4e2de] hover:bg-[#fbf9f5] text-stone-600 font-bold py-2 px-3.5 rounded-xl flex items-center gap-1 cursor-pointer transition-colors disabled:opacity-60"
        @click="handleRefresh"
        :disabled="refreshing"
      >
        <RefreshCw :class="refreshing ? 'animate-spin' : ''" class="w-3.5 h-3.5" />
        <span>{{ refreshing ? '刷新中…' : (justRefreshed ? '已更新 ✓' : '刷新重置') }}</span>
      </button>
    </template>

    <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-6">
      <div class="flex flex-wrap gap-1.5">
        <button
          v-for="rank in ranks"
          :key="rank"
          @click="selectedRank = rank; handleSearch()"
          :class="`px-3.5 py-2 text-xs font-bold rounded-lg transition-all cursor-pointer ${
            selectedRank === rank
              ? 'bg-[#ffdbcc] text-[#351000]'
              : 'bg-[#f5f3ef] text-stone-600 hover:text-stone-900'
          }`"
        >
          {{ rank }}
        </button>
      </div>

      <div class="relative w-full md:w-64">
        <input
          type="text"
          v-model="searchKeyword"
          @keyup.enter="handleSearch"
          placeholder="搜索昵称、账号、手机号..."
          class="w-full text-xs pl-8 pr-3 py-2 rounded-lg bg-[#f5f3ef] border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] text-stone-800 transition-colors"
        />
        <Search class="w-3.5 h-3.5 text-stone-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
      </div>
    </div>

    <div class="overflow-x-auto">
      <table class="w-full border-collapse text-left text-xs">
        <thead>
          <tr class="border-b border-[#e4e2de] text-stone-450 uppercase font-mono tracking-wider">
            <th class="py-3 px-4 font-bold">会员信息</th>
            <th class="py-3 px-4 font-bold">账号/ID</th>
            <th class="py-3 px-4 font-bold">手机/邮箱</th>
            <th class="py-3 px-4 font-bold text-center">会员级别</th>
            <th class="py-3 px-4 font-bold text-right">消费积分</th>
            <th class="py-3 px-4 font-bold text-right">钱包余额</th>
            <th class="py-3 px-4 font-bold">注册日期</th>
            <th class="py-3 px-4 font-bold text-center">状态</th>
            <th class="py-3 px-4 font-bold text-right">管理操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-stone-100">
          <tr
            v-for="customer in pagedCustomers"
            :key="customer.id"
            class="hover:bg-[#fbf9f5] transition-colors"
          >
            <td class="py-3.5 px-4">
              <div class="flex items-center gap-3">
                <div class="w-9 h-9 rounded-full bg-gradient-to-br from-[#ff6b35] to-[#ffdbcc] flex items-center justify-center text-white font-bold">
                  {{ (customer.nickname || '麦')[0] }}
                </div>
                <span class="font-bold text-stone-850 text-xs">{{ customer.nickname || '麦乐迪用户' }}</span>
              </div>
            </td>

            <td class="py-3.5 px-4 font-mono text-stone-600">
              <span class="block font-semibold">{{ customer.username }}</span>
              <span class="text-[10px] text-stone-400">U{{ String(customer.id).padStart(5, '0') }}</span>
            </td>

            <td class="py-3.5 px-4 text-stone-600 font-mono">
              <div class="flex items-center gap-1.5">
                <Phone class="w-3 h-3 text-stone-400" />
                <span>{{ customer.phone || '—' }}</span>
              </div>
              <div class="flex items-center gap-1.5 mt-1 text-[10px] text-stone-400">
                <Mail class="w-3 h-3" />
                <span class="truncate max-w-[180px]">{{ customer.email || '—' }}</span>
              </div>
            </td>

            <td class="py-3.5 px-4 text-center">
              <span :class="`px-2.5 py-0.5 rounded-md text-[9px] font-black ${rankClass(customer.rank)}`">
                {{ customer.rank || '普通会员' }}
              </span>
            </td>

            <td class="py-3.5 px-4 text-right font-bold text-stone-750 font-mono">
              {{ customer.points || 0 }}
            </td>

            <td class="py-3.5 px-4 text-right font-black text-stone-800 font-mono">
              ￥{{ Number(customer.balance || 0).toFixed(2) }}
            </td>

            <td class="py-3.5 px-4 text-stone-500 font-mono">
              {{ formatDate(customer.joinDate) }}
            </td>

            <td class="py-3.5 px-4 text-center">
              <span :class="`px-2 py-0.5 rounded-full text-[9px] font-bold ${
                customer.status !== 0 ? 'bg-green-50 text-green-600 border border-green-200' : 'bg-red-50 text-red-600 border border-red-200'
              }`">
                {{ customer.status !== 0 ? '正常' : '已锁定' }}
              </span>
            </td>

            <td class="py-3.5 px-4 text-right space-x-1">
              <button
                @click="handleGiftPoints(customer)"
                class="px-2 py-1 bg-[#ff6b35]/10 text-[#ff6b35] hover:bg-[#ff6b35] hover:text-white rounded-lg font-bold text-[10px] transition-all cursor-pointer"
                title="赠送积分"
              >
                <Gift class="w-3 h-3 inline" />
                <span class="ml-0.5">赠送积分</span>
              </button>

              <button
                @click="handleToggleStatus(customer)"
                :class="`px-2 py-1 rounded-lg font-bold text-[10px] transition-all cursor-pointer inline-flex items-center gap-0.5 ${
                  customer.status !== 0
                    ? 'border border-red-150 hover:bg-red-50 text-red-500'
                    : 'bg-green-600 text-white hover:bg-green-700'
                }`"
              >
                <UserX v-if="customer.status !== 0" class="w-3 h-3" />
                <UserCheck v-else class="w-3 h-3" />
                <span>{{ customer.status !== 0 ? '冻结' : '解冻' }}</span>
              </button>
            </td>
          </tr>
          <tr v-if="filteredCustomers.length === 0">
            <td colspan="9" class="text-center py-10 text-stone-400 text-xs">没有符合条件的用户</td>
          </tr>
        </tbody>
      </table>
      <Pagination
        :total="filteredCustomers.length"
        v-model:page="currentPage"
        :size="pageSize"
      />
    </div>

  </AdminLayout>
</template>