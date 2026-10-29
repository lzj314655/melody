<script setup>
import { ref } from "vue";
import AdminLayout from "@/components/AdminLayout.vue";
import { resetDemoDataApi } from "@/api/admin";
import { Save, ShieldAlert, Sliders, Database, EyeOff, Radio, RefreshCcw } from "lucide-vue-next";

// Shop variables
const shopName = ref("麦乐迪零食商场 (Melody Savory Shop)");
const freeShippingLimit = ref(99);
const defaultShippingFee = ref(15);
const supportReturnIn7Days = ref(true);
const pointsPerYuan = ref(1); // 1 yuan spent = 1 point rewarded
const alertMsg = ref("");

const handleSaveConfig = () => {
  if (!shopName.value || freeShippingLimit.value < 0 || defaultShippingFee.value < 0) {
    alertMsg.value = "请填写完整并确保数值合理！";
    return;
  }

  localStorage.setItem("melody_sys_shopName", shopName.value);
  localStorage.setItem("melody_sys_freeShippingLimit", String(freeShippingLimit.value));
  localStorage.setItem("melody_sys_defaultShippingFee", String(defaultShippingFee.value));
  localStorage.setItem("melody_sys_supportReturn", String(supportReturnIn7Days.value));
  localStorage.setItem("melody_sys_pointsRatio", String(pointsPerYuan.value));

  alert("系统参数配置已成功保存！并应用至前台商城。");
  alertMsg.value = "";
};

const handleResetDatabase = () => {
  if (confirm("警告：确定要重置并恢复商城默认的演示数据吗？这将会清空所有的购买订单、新增收货地址和自定义商品！")) {
    localStorage.removeItem("melody_products");
  localStorage.removeItem("melody_user");
  localStorage.removeItem("melody_coupons");
  localStorage.removeItem("melody_addresses");
  localStorage.removeItem("melody_orders");
  localStorage.removeItem("melody_campaigns");
    localStorage.removeItem("melody_logs");
    alert("商城核心数据库已恢复至出厂配置！即将刷新页面。");
    window.location.reload();
  }
};

// 后端真实演示数据重置——调 POST /api/admin/system/data-reset
// 后端目前为 stub（返回 success 但不真清库），给评委演示 / 一键回到初始状态用。
const resetBackendLoading = ref(false);
const handleResetBackendData = async () => {
  if (!confirm("确定要调用后端重置接口吗？\n当前后端实现为 stub（仅返回成功，不会真清库）。\n如需真实清空数据库，请直接执行 sql/reset.sql。")) return;
  resetBackendLoading.value = true;
  try {
    await resetDemoDataApi();
    alert("后端演示数据重置请求已发送。");
  } catch (e) {
    alert("重置失败：" + (e.message || e));
  } finally {
    resetBackendLoading.value = false;
  }
};
</script>

<template>
  <AdminLayout 
    title="系统设置" 
    subtitle="配置麦乐迪商城的前台商铺信息、订单包邮门槛、售后服务保障及数据库参数"
    :breadcrumbs="['首页', '系统设置']"
  >
    <div class="space-y-8 max-w-4xl">
      
      <!-- General Settings Group -->
      <div class="space-y-6">
        <h3 class="text-sm font-bold text-stone-700 flex items-center gap-2 border-b border-stone-100 pb-3 font-display">
          <Sliders class="w-4.5 h-4.5 text-[#ff6b35]" />
          <span>商城核心运营参数</span>
        </h3>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-6">
          <!-- Shop name -->
          <div class="sm:col-span-2">
            <label class="block text-xs font-bold text-stone-600 mb-2">商铺公开名称</label>
            <input
              type="text"
              v-model="shopName"
              placeholder="请输入商铺对外展示名称"
              class="w-full text-xs px-3.5 py-2.5 rounded-xl bg-[#f5f3ef] border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] text-stone-850"
            />
          </div>

          <!-- Free shipping limit -->
          <div>
            <label class="block text-xs font-bold text-stone-600 mb-2">满额包邮门槛 (元)</label>
            <input
              type="number"
              v-model.number="freeShippingLimit"
              class="w-full text-xs px-3.5 py-2.5 rounded-xl bg-[#f5f3ef] border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] font-mono"
            />
            <p class="text-[10px] text-stone-400 mt-1">单笔订单结算实付满此金额免收物流邮费</p>
          </div>

          <!-- Shipping fee -->
          <div>
            <label class="block text-xs font-bold text-stone-600 mb-2">默认运费费用 (元)</label>
            <input
              type="number"
              v-model.number="defaultShippingFee"
              class="w-full text-xs px-3.5 py-2.5 rounded-xl bg-[#f5f3ef] border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35] font-mono"
            />
            <p class="text-[10px] text-stone-400 mt-1">不满包邮门槛时加收的标准顺丰陆运资费</p>
          </div>

          <!-- Return goods -->
          <div class="flex items-center justify-between p-4 bg-[#fbf9f5] border border-stone-150 rounded-2xl sm:col-span-2">
            <div>
              <h5 class="text-xs font-bold text-stone-800">支持 7 天无理由退换货</h5>
              <p class="text-[10px] text-stone-400 mt-0.5">勾选后，商品详情页将点亮 100% 售后安心保障图标</p>
            </div>
            <input type="checkbox" v-model="supportReturnIn7Days" class="w-4 h-4 accent-[#ff6b35]" />
          </div>

          <!-- Points rule -->
          <div class="sm:col-span-2">
            <label class="block text-xs font-bold text-stone-600 mb-2">积分赠送比例 (消费 ￥1 元赠送积分数)</label>
            <select 
              v-model.number="pointsPerYuan"
              class="w-full text-xs px-3.5 py-2.5 rounded-xl bg-[#f5f3ef] border border-[#e4e2de] focus:outline-none focus:border-[#ff6b35]"
            >
              <option :value="1">消费 1 元赠送 1 会员积分 (推荐)</option>
              <option :value="2">消费 1 元赠送 2 会员积分 (节日促销)</option>
              <option :value="0.5">消费 1 元赠送 0.5 会员积分</option>
            </select>
          </div>
        </div>

        <div v-if="alertMsg" class="bg-red-50 text-red-600 rounded-xl p-3 text-xs flex items-center gap-1.5 border border-red-100">
          <ShieldAlert class="w-4 h-4 shrink-0" />
          <span>{{ alertMsg }}</span>
        </div>

        <button
          @click="handleSaveConfig"
          class="px-5 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white font-bold rounded-xl text-xs flex items-center gap-1.5 cursor-pointer shadow-sm active:scale-95 transition-all"
        >
          <Save class="w-4 h-4" />
          <span>保存运营参数</span>
        </button>
      </div>

      <div class="border-t border-[#e4e2de] my-8"></div>

      <!-- Advanced Database Maintenance Group -->
      <div class="space-y-4">
        <h3 class="text-sm font-bold text-red-600 flex items-center gap-2 border-b border-stone-100 pb-3 font-display">
          <Database class="w-4.5 h-4.5" />
          <span>灾备与系统维护 (敏感操作)</span>
        </h3>

        <div class="p-5 bg-red-50/40 rounded-2xl border border-red-200/50 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <div class="max-w-xl">
            <h5 class="text-xs font-bold text-stone-800">重置并清理 LocalStorage 模拟数据库</h5>
            <p class="text-[10px] text-stone-500 leading-relaxed mt-1">
              清除管理员、用户、商品及历史订单在 LocalStorage 中的缓存。这常用于测试极端边界状态或将平台一键回滚还原至初始的新店状态。操作不可逆，请谨慎！
            </p>
          </div>

          <button
            @click="handleResetDatabase"
            class="px-4 py-2.5 bg-red-600 hover:bg-red-750 text-white font-bold rounded-xl text-xs cursor-pointer shadow-sm transition-colors shrink-0"
          >
            一键清空并重置核心库
          </button>
        </div>

        <div class="p-5 bg-amber-50/40 rounded-2xl border border-amber-200/50 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <div class="max-w-xl">
            <h5 class="text-xs font-bold text-stone-800">调用后端演示数据重置接口</h5>
            <p class="text-[10px] text-stone-500 leading-relaxed mt-1">
              触发 <code class="font-mono text-[10px] bg-amber-100 px-1 rounded">POST /api/admin/system/data-reset</code>。
              当前后端为 stub（仅返回成功响应），用于在演示时一键"回到初始状态"的体感效果；真实清库请执行 <code class="font-mono text-[10px] bg-amber-100 px-1 rounded">sql/reset.sql</code>。
            </p>
          </div>

          <button
            @click="handleResetBackendData"
            :disabled="resetBackendLoading"
            class="px-4 py-2.5 bg-amber-600 hover:bg-amber-700 disabled:bg-stone-300 text-white font-bold rounded-xl text-xs cursor-pointer shadow-sm transition-colors shrink-0 flex items-center gap-1.5"
          >
            <RefreshCcw :class="`w-3.5 h-3.5 ${resetBackendLoading ? 'animate-spin' : ''}`" />
            <span>{{ resetBackendLoading ? '请求中…' : '调用后端重置' }}</span>
          </button>
        </div>
      </div>

    </div>
  </AdminLayout>
</template>
