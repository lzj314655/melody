<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useCartStore } from "@/store/cart";
import { useUserStore } from "@/store/user";
import { fetchWalletBalance, orderAlipayPayApi } from "@/api/user";
import { couponFace } from "@/utils/coupon";
import { reportBehavior } from "@/api/behavior";
import FrontendLayout from "@/components/FrontendLayout.vue";
import { Trash2, AlertCircle, MapPin, Ticket, CreditCard, ArrowRight, ShoppingBag, Wallet } from "lucide-vue-next";

const router = useRouter();
const cartStore = useCartStore();
const userStore = useUserStore();

const showCheckoutSuccess = ref(false);
const createdOrderNo = ref("");
const lastOrderAmount = ref(0);

const selectedAddressId = ref(null);
const walletBalance = ref(0);

onMounted(async () => {
  await userStore.loadUser().catch(() => {});
  await userStore.loadAddresses().catch(() => {});
  await userStore.loadCoupons().catch(() => {});
  await cartStore.loadCart().catch(() => {});
  // 余额单独从钱包接口取
  try {
    const w = await fetchWalletBalance();
    walletBalance.value = Number(w?.balance ?? 0);
    if (userStore.user) userStore.user.balance = walletBalance.value;
  } catch (e) {
    walletBalance.value = userStore.user?.balance || 0;
  }
  if (userStore.addresses.length > 0) {
    const def = userStore.addresses.find(a => a.isDefault) || userStore.addresses[0];
    selectedAddressId.value = def.id;
  }
});

const currentAddressObj = computed(() => {
  return userStore.addresses.find(a => a.id === selectedAddressId.value) || userStore.addresses[0];
});

const selectedFinal = computed(() => {
  const price = cartStore.selectedItemsPrice;
  const coupon = cartStore.selectedCoupon;
  const discount = (coupon && price >= (coupon.minSpend || coupon.minAmount || 0))
    ? Number(coupon.discount || 0)
    : 0;
  return Math.max(0, price - discount);
});

const isSelectedBalanceInsufficient = computed(() => {
  return walletBalance.value < selectedFinal.value;
});

const submitting = ref(false);
const payMethod = ref("WALLET"); // WALLET 余额 / ALIPAY 支付宝沙箱

const handleCheckout = async () => {
  if (submitting.value) return; // 防抖
  if (!selectedAddressId.value) {
    alert("请先选择收货地址！");
    return;
  }
  if (cartStore.selectedIds.length === 0) {
    alert("请先勾选要结算的商品");
    return;
  }
  if (payMethod.value === "WALLET" && isSelectedBalanceInsufficient.value) {
    alert("账户余额不足，请到个人中心充值或选择支付宝支付！");
    return;
  }
  submitting.value = true;
  // 支付宝方式：先开窗口保住点击手势，避免 await 后被弹窗拦截
  const payWin = payMethod.value === "ALIPAY" ? window.open("", "_blank") : null;
  try {
    const userCouponId = cartStore.selectedCoupon?.id || null;
    const itemIds = [...cartStore.selectedIds]; // checkout 会清空 selectedIds，先快照
    const order = await cartStore.checkout(selectedAddressId.value, userCouponId, payMethod.value);
    reportBehavior("ORDER", null, {
      orderId: order.id,
      cartItemIds: itemIds,
      totalPrice: order.payAmount ?? order.totalAmount ?? order.totalPrice ?? 0,
    });

    if (payMethod.value === "ALIPAY") {
      // 订单已建为 PENDING_PAY，拉支付宝支付表单写入新窗口
      const { payForm } = await orderAlipayPayApi(order.id);
      if (!payWin) {
        alert("请允许浏览器弹窗以打开支付宝支付页");
      } else {
        payWin.document.write(payForm);
        payWin.document.close();
      }
      router.push(`/order/${order.id}?alipay=1`);
    } else {
      createdOrderNo.value = order.id;
      lastOrderAmount.value = Number(order.payAmount ?? order.totalPrice ?? 0);
      showCheckoutSuccess.value = true;
    }
  } catch (err) {
    if (payWin) payWin.close();
    alert(err.message || "结算失败");
  } finally {
    submitting.value = false;
  }
};

// 只展示满足门槛的券；当前已选中的券即便不满足也保留显示（已锁定状态）
const availableCoupons = computed(() => {
  const price = cartStore.selectedItemsPrice;
  const lockedId = cartStore.selectedCoupon?.id;
  return (userStore.coupons || []).filter(c => {
    const min = Number(c.minSpend || c.minAmount || 0);
    return price >= min || c.id === lockedId;
  });
});

const selectCoupon = (coupon) => {
  if (!coupon) return;
  const min = Number(coupon.minSpend || coupon.minAmount || 0);
  if (cartStore.selectedItemsPrice < min) return; // 门槛不够，禁止选择
  if (cartStore.selectedCoupon?.id === coupon.id) {
    cartStore.selectedCoupon = null;       // 取消勾选
    return;
  }
  // 门槛判定：只对已选商品生效（优惠 F-U-034）
  if (cartStore.selectedItemsPrice < (coupon.minSpend || 0)) {
    const diff = (coupon.minSpend || 0) - cartStore.selectedItemsPrice;
    alert(
      `已选商品 ￥${cartStore.selectedItemsPrice.toFixed(2)} 未达该券门槛 ￥${coupon.minSpend}，\n` +
      `还需 ￥${diff.toFixed(2)}。请勾选更多商品后再使用。`
    );
    return;
  }
  cartStore.selectedCoupon = coupon;
};
</script>

<template>
  <FrontendLayout>
    <div class="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- Checkout Success Modal -->
      <div
        v-if="showCheckoutSuccess"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      >
        <div class="bg-white rounded-3xl border border-[#e4e2de] p-8 max-w-md w-full text-center shadow-2xl animate-scale-up">
          <div class="w-16 h-16 rounded-full bg-green-100 text-green-600 flex items-center justify-center mx-auto mb-4">
            <span class="material-symbols-outlined text-4xl font-bold">check</span>
          </div>
          <h2 class="text-xl font-bold text-stone-800 font-display">麦乐迪支付成功！</h2>
          <p class="text-xs text-stone-500 mt-2">
            我们已从您的钱包余额扣除并为您安排配货。
          </p>

          <div class="bg-[#fbf9f5] border border-[#e4e2de] rounded-2xl p-4 my-5 text-left text-xs space-y-1.5">
            <div class="flex justify-between">
              <span class="text-stone-400">订单单号</span>
              <span class="font-bold text-stone-800 font-mono">#{{ createdOrderNo }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-stone-400">应付总额</span>
              <span class="font-bold text-[#ff6b35] font-display">￥{{ lastOrderAmount.toFixed(2) }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-stone-400">收货地址</span>
              <span class="font-bold text-stone-700 truncate max-w-[200px]">{{ currentAddressObj?.detail }}</span>
            </div>
          </div>

          <button
            @click="showCheckoutSuccess = false; router.push('/order')"
            class="w-full py-3 bg-[#004e64] hover:bg-[#004e64]/90 text-white font-bold text-xs rounded-xl flex items-center justify-center gap-1 cursor-pointer"
          >
            <span>查看我的订单</span>
            <ArrowRight class="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <!-- Page Heading -->
      <div class="flex items-center gap-2.5 mb-6 border-b border-stone-100 pb-4 shrink-0">
        <h1 class="text-2xl font-bold tracking-tight text-stone-800 font-display">我的购物车</h1>
        <span class="text-xs px-2.5 py-1 bg-[#ffdbcc] text-[#ff6b35] rounded-full font-bold">
          {{ cartStore.cartCount }} 件零食
        </span>
      </div>

      <!-- Empty state -->
      <div
        v-if="cartStore.items.length === 0"
        class="flex flex-col items-center justify-center py-24 bg-white border border-[#e4e2de] rounded-3xl"
      >
        <div class="w-16 h-16 rounded-full bg-[#ffdbcc]/40 flex items-center justify-center text-[#ff6b35] mb-4">
          <ShoppingBag class="w-8 h-8" />
        </div>
        <h4 class="text-base font-bold text-stone-700">购物车里还是空的哦</h4>
        <p class="text-xs text-stone-400 mt-1 max-w-xs text-center leading-relaxed">
          麦乐迪零食屋里有大把好吃的在向你招手，快去挑选喜爱的美味吧~
        </p>
        <button
          @click="router.push('/')"
          class="mt-5 px-5 py-2.5 bg-[#ff6b35] hover:bg-[#ff6b35]/90 text-white text-xs font-bold rounded-xl cursor-pointer shadow-sm transform active:scale-95 transition-all"
        >
          去挑选美味
        </button>
      </div>

      <!-- Cart Items Grid -->
      <div v-else class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        <div class="lg:col-span-8 flex flex-col gap-4">
          <div class="bg-white border border-[#e4e2de] rounded-3xl p-6 shadow-sm">
            <div class="flex items-center justify-between mb-4">
              <h3 class="text-xs font-bold text-stone-400 uppercase tracking-widest font-mono">商品清单</h3>
              <div class="text-[10px] text-stone-400 font-mono">
                已选 <strong class="text-[#ff6b35]">{{ cartStore.selectedCount }}</strong> / 共 {{ cartStore.items.length }} 件
              </div>
            </div>

            <!-- 全选 -->
            <div class="flex items-center gap-2.5 py-2.5 border-b border-stone-100 mb-1">
              <label class="flex items-center gap-2 cursor-pointer select-none">
                <input
                  type="checkbox"
                  :checked="cartStore.isAllSelected"
                  @change="cartStore.toggleSelectAll()"
                  class="w-4 h-4 accent-[#ff6b35] cursor-pointer"
                />
                <span class="text-[11px] font-bold text-stone-700">全选</span>
              </label>
              <span v-if="cartStore.isAllSelected" class="text-[10px] text-[#ff6b35] font-bold">已选全部</span>
            </div>

            <div class="divide-y divide-stone-100">
              <div
                v-for="item in cartStore.items"
                :key="item.id"
                :class="`py-4.5 first:pt-0 last:pb-0 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 ${
                  cartStore.selectedIds.includes(item.id) ? 'bg-[#fff8f4]/40 -mx-2 px-2 rounded-xl' : ''
                }`"
              >
                <div class="flex items-center gap-3.5 min-w-0 flex-1">
                  <input
                    type="checkbox"
                    :checked="cartStore.selectedIds.includes(item.id)"
                    @change="cartStore.toggleSelect(item.id)"
                    class="w-4 h-4 accent-[#ff6b35] cursor-pointer shrink-0"
                  />
                  <div class="w-16 h-16 rounded-xl bg-[#fbf9f5] border border-stone-200 flex items-center justify-center text-stone-400 text-xs font-mono overflow-hidden">
                    <img
                      v-if="item.productImage"
                      :src="item.productImage"
                      :alt="item.productName"
                      class="w-full h-full object-cover"
                    />
                    <span v-else>#{{ item.productId }}</span>
                  </div>
                  <div class="min-w-0">
                    <h4
                      @click="router.push(`/product/${item.productId}`)"
                      class="text-xs sm:text-sm font-bold text-stone-800 hover:text-[#ff6b35] cursor-pointer truncate leading-tight"
                    >
                      {{ item.productName || `商品 #${item.productId}` }}
                    </h4>
                    <p class="text-[10px] text-stone-400 mt-1">规格：<span class="font-bold text-stone-600">{{ item.specName || '默认' }}</span></p>
                    <p class="text-xs font-bold text-[#ff6b35] mt-1 sm:hidden">￥{{ Number(item.price).toFixed(2) }}</p>
                  </div>
                </div>

                <div class="flex items-center justify-between sm:justify-end gap-6 w-full sm:w-auto">
                  <div class="text-right hidden sm:block">
                    <span class="text-[10px] text-stone-400 block font-semibold">单价</span>
                    <span class="text-xs font-bold text-stone-800 font-mono">￥{{ Number(item.price).toFixed(2) }}</span>
                  </div>

                  <div class="flex items-center border border-[#e4e2de] rounded-xl bg-white p-0.5">
                    <button
                      @click="cartStore.updateQuantity(item.productId, item.skuId, item.quantity - 1)"
                      class="w-7 h-7 rounded-lg flex items-center justify-center hover:bg-[#efeeea] text-stone-600 font-bold text-xs cursor-pointer"
                    >
                      -
                    </button>
                    <span class="w-8 text-center text-xs font-bold text-stone-800 font-mono">{{ item.quantity }}</span>
                    <button
                      @click="cartStore.updateQuantity(item.productId, item.skuId, item.quantity + 1)"
                      class="w-7 h-7 rounded-lg flex items-center justify-center hover:bg-[#efeeea] text-stone-600 font-bold text-xs cursor-pointer"
                    >
                      +
                    </button>
                  </div>

                  <div class="text-right">
                    <span class="text-[10px] text-stone-400 block font-semibold">小计</span>
                    <span class="text-xs sm:text-sm font-bold text-stone-800 font-mono">￥{{ (Number(item.price) * item.quantity).toFixed(2) }}</span>
                  </div>

                  <button
                    @click="cartStore.removeFromCart(item.productId, item.skuId)"
                    class="p-2 text-stone-400 hover:text-red-500 rounded-lg hover:bg-red-50 cursor-pointer"
                    title="删除"
                  >
                    <Trash2 class="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Address -->
          <div class="bg-white border border-[#e4e2de] rounded-3xl p-6 shadow-sm">
            <h3 class="text-xs font-bold text-stone-400 uppercase tracking-widest font-mono mb-4 flex items-center gap-1.5">
              <MapPin class="w-4.5 h-4.5 text-[#ff6b35]" />
              <span>选择收货地址</span>
            </h3>
            <div v-if="userStore.addresses.length === 0" class="text-center py-6 border border-dashed border-[#e4e2de] rounded-2xl">
              <p class="text-xs text-stone-500 mb-2">您还没有添加收货地址哦</p>
              <button
                @click="router.push('/address')"
                class="px-3.5 py-1.5 bg-[#004e64]/10 text-[#004e64] hover:bg-[#004e64]/20 rounded-xl text-xs font-bold cursor-pointer"
              >
                新增地址
              </button>
            </div>
            <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div
                v-for="addr in userStore.addresses"
                :key="addr.id"
                @click="selectedAddressId = addr.id"
                :class="`p-4 rounded-2xl border cursor-pointer transition-all ${
                  selectedAddressId === addr.id
                    ? 'border-[#ff6b35] bg-[#ffdbcc]/20 shadow-sm'
                    : 'border-[#e4e2de] hover:border-stone-400 bg-white'
                }`"
              >
                <div class="flex items-center justify-between mb-2">
                  <span class="text-xs font-bold text-stone-800">{{ addr.consignee || addr.name || '收货人' }}</span>
                  <span class="text-[10px] px-2 py-0.5 bg-[#efeeea] text-[#5a4136] rounded-md font-bold font-mono">{{ addr.tag || '家' }}</span>
                </div>
                <p class="text-xs text-stone-600 font-medium leading-none">{{ addr.phone }}</p>
                <p class="text-[11px] text-stone-400 mt-2 line-clamp-2 leading-relaxed">
                  {{ addr.province || '' }} {{ addr.city || '' }} {{ addr.district || '' }} {{ addr.detail || '' }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <!-- Right side coupons + summary -->
        <div class="lg:col-span-4 flex flex-col gap-6">
          <div class="bg-white border border-[#e4e2de] rounded-3xl p-6 shadow-sm">
            <h3 class="text-xs font-bold text-stone-400 uppercase tracking-widest font-mono mb-4 flex items-center gap-1.5">
              <Ticket class="w-4.5 h-4.5 text-[#ff6b35]" />
              <span>使用优惠券</span>
            </h3>

            <div v-if="availableCoupons.length === 0" class="text-center py-4 bg-[#fbf9f5] border border-[#e4e2de] rounded-2xl text-xs text-stone-500">
              暂无可用优惠券
            </div>

            <div v-else class="space-y-2 max-h-56 overflow-y-auto">
              <div
                v-for="coupon in availableCoupons"
                :key="coupon.id"
                @click="selectCoupon(coupon)"
                :class="`p-3 border rounded-2xl cursor-pointer transition-all flex items-center justify-between ${
                  cartStore.selectedCoupon?.id === coupon.id
                    ? 'border-[#ff6b35] bg-[#ffdbcc]/40'
                    : 'border-[#e4e2de] hover:border-stone-400 bg-white'
                }`"
              >
                <div class="flex items-center gap-2.5">
                  <span class="text-xs font-black text-[#ff6b35] font-display">
                    <template v-for="(cf, i) in [couponFace(coupon.type, coupon.discount)]" :key="i">
                      {{ cf.prefix }}{{ cf.value }}{{ cf.suffix }}
                    </template>
                  </span>
                  <div>
                    <h5 class="text-[11px] font-bold text-stone-800 leading-tight">{{ coupon.name || coupon.title || '优惠券' }}</h5>
                    <p class="text-[9px] text-stone-400 mt-0.5">门槛：满 ￥{{ coupon.minSpend || 0 }}</p>
                  </div>
                </div>
                <span :class="`w-4.5 h-4.5 rounded-full border-2 flex items-center justify-center ${
                  cartStore.selectedCoupon?.id === coupon.id
                    ? 'border-[#ff6b35] bg-[#ff6b35]'
                    : 'border-[#dbdad6]'
                }`">
                  <span v-if="cartStore.selectedCoupon?.id === coupon.id" class="w-1.5 h-1.5 bg-white rounded-full"></span>
                </span>
              </div>
            </div>
          </div>

          <div class="bg-white border border-[#e4e2de] rounded-3xl p-6 shadow-sm">
            <h3 class="text-xs font-bold text-stone-400 uppercase tracking-widest font-mono mb-4 flex items-center gap-1.5">
              <CreditCard class="w-4.5 h-4.5 text-[#ff6b35]" />
              <span>结算汇总</span>
            </h3>

            <div class="space-y-3.5 text-xs">
              <div class="flex justify-between text-stone-500">
                <span>商品总额（已选 {{ cartStore.selectedCount }} 件）</span>
                <span class="font-bold font-mono">￥{{ cartStore.selectedItemsPrice.toFixed(2) }}</span>
              </div>

              <div class="flex justify-between text-stone-500">
                <span>运费</span>
                <span class="font-bold text-green-600 font-mono">免邮 ￥0.00</span>
              </div>

              <div v-if="cartStore.selectedCoupon && cartStore.selectedItemsPrice >= (cartStore.selectedCoupon.minSpend || 0)" class="flex justify-between text-stone-500">
                <span class="text-[#ff6b35]">优惠券折扣</span>
                <span class="font-bold text-[#ff6b35] font-mono">-￥{{ Number(cartStore.selectedCoupon.discount).toFixed(2) }}</span>
              </div>

              <div class="h-px bg-stone-100 my-2"></div>

              <div class="flex justify-between text-base">
                <span class="font-bold text-stone-800">实付总金额</span>
                <span class="font-black text-[#ff6b35] font-display">￥{{ selectedFinal.toFixed(2) }}</span>
              </div>
            </div>

            <div
              v-if="userStore.user"
              class="mt-5 p-3 rounded-2xl border border-stone-200/60 bg-[#fbf9f5] flex items-center justify-between text-xs"
            >
              <div class="flex items-center gap-1.5">
                <span class="material-symbols-outlined text-stone-400 text-lg">account_balance_wallet</span>
                <span class="text-stone-500">会员余额</span>
              </div>
              <div class="text-right">
                <span class="font-bold text-stone-800 font-mono">￥{{ walletBalance.toFixed(2) }}</span>
                <span
                  v-if="isSelectedBalanceInsufficient"
                  class="block text-[9px] text-red-500 font-bold"
                >
                  余额不足，请充值
                </span>
              </div>
            </div>

            <div class="mt-5 grid grid-cols-2 gap-2">
              <button type="button" @click="payMethod = 'WALLET'"
                :class="`p-3 rounded-2xl border text-left transition-all cursor-pointer ${payMethod === 'WALLET' ? 'border-[#ff6b35] bg-[#fff4ef]' : 'border-stone-200 bg-white hover:bg-[#fbf9f5]'}`">
                <div class="flex items-center gap-1.5">
                  <Wallet class="w-4 h-4 text-[#ff6b35]" />
                  <span class="text-xs font-bold text-stone-700">余额支付</span>
                </div>
                <p class="text-[10px] text-stone-400 mt-0.5">￥{{ walletBalance.toFixed(2) }} 可用</p>
              </button>
              <button type="button" @click="payMethod = 'ALIPAY'"
                :class="`p-3 rounded-2xl border text-left transition-all cursor-pointer ${payMethod === 'ALIPAY' ? 'border-[#ff6b35] bg-[#fff4ef]' : 'border-stone-200 bg-white hover:bg-[#fbf9f5]'}`">
                <div class="flex items-center gap-1.5">
                  <CreditCard class="w-4 h-4 text-[#1677ff]" />
                  <span class="text-xs font-bold text-stone-700">支付宝</span>
                </div>
                <p class="text-[10px] text-stone-400 mt-0.5">沙箱在线支付</p>
              </button>
            </div>

            <button
              @click="handleCheckout"
              :disabled="submitting || cartStore.selectedCount === 0 || userStore.addresses.length === 0 || (payMethod === 'WALLET' && isSelectedBalanceInsufficient)"
              class="w-full mt-3 py-4 bg-[#ff6b35] disabled:bg-stone-300 disabled:cursor-not-allowed text-white hover:bg-[#ff6b35]/90 font-bold text-xs rounded-xl flex items-center justify-center gap-1 shadow-md transform active:scale-95 transition-all cursor-pointer"
            >
              <span v-if="submitting">处理中...</span>
              <span v-else>{{ payMethod === 'ALIPAY' ? '支付宝付款' : '立即付款' }} ￥{{ selectedFinal.toFixed(2) }}</span>
              <ArrowRight class="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </FrontendLayout>
</template>

<style scoped>
@keyframes scaleUp {
  from { opacity: 0; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1); }
}
.animate-scale-up {
  animation: scaleUp 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}
</style>
