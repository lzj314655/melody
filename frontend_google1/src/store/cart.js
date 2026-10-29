import { defineStore } from "pinia";
import {
  getCartItemsApi,
  addCartItemApi,
  updateCartItemApi,
  removeCartItemApi,
  selectCartItemApi,
  selectAllCartApi,
} from "@/api/cart";
import { createOrderApi } from "@/api/admin";
import { useUserStore } from "./user";

// 购物车项：后端 CartItemVO 扁平字段（含商品名/封面图冗余）
// 行结构：{ id, productId, skuId, quantity, price, specName, selected,
//          productName, productImage }

export const useCartStore = defineStore("cart", {
  state: () => ({
    items: [],     // 完整购物车（来自 /api/cart）
    selectedIds: [], // 当前选中的 cart item id（F-U-034 多选）
    selectedCoupon: null,
  }),
  getters: {
    cartCount: (state) => state.items.reduce((acc, item) => acc + (item.quantity || 0), 0),
    itemsPrice: (state) =>
      state.items.reduce((acc, item) => acc + Number(item.price || 0) * (item.quantity || 0), 0),
    discountAmount() {
      if (!this.selectedCoupon) return 0;
      const price = this.itemsPrice;
      if (price >= (this.selectedCoupon.minSpend || this.selectedCoupon.minAmount || 0)) {
        return Number(this.selectedCoupon.discount || this.selectedCoupon.amount || 0);
      }
      return 0;
    },
    finalPrice() {
      return Math.max(0, this.itemsPrice - this.discountAmount);
    },
    selectedItemsList: (state) => {
      const set = new Set(state.selectedIds);
      return state.items.filter(i => set.has(i.id));
    },
    selectedCount: (state) => state.selectedIds.length,
    selectedItemsPrice() {
      return this.selectedItemsList.reduce(
        (acc, i) => acc + Number(i.price || 0) * (i.quantity || 0), 0
      );
    },
    isAllSelected: (state) =>
      state.items.length > 0 && state.selectedIds.length === state.items.length,
  },
  actions: {
    async loadCart() {
      const items = await getCartItemsApi();
      this.items = (items || []).map(i => ({
        ...i,
        selected: i.selected === 1 || i.selected === true,
      }));
      // 同步已选列表
      this.selectedIds = this.items.filter(i => i.selected).map(i => i.id);
    },
    async addToCart(product, spec, quantity = 1, price = null) {
      // 兼容旧调用：spec 既可能是 "辣味/60g" 字符串，也可能是 skuId
      // 后端需要 skuId，所以采用约定：传入的 product 对象里有 skuId
      const skuId = product.skuId || (typeof spec === "number" ? spec : null);
      if (!skuId) throw new Error("缺少 skuId");
      await addCartItemApi(product.id, skuId, quantity);
      await this.loadCart();
    },
    async updateQuantity(productId, skuId, quantity) {
      if (quantity <= 0) {
        await removeCartItemApi(productId, skuId);
      } else {
        await updateCartItemApi(productId, skuId, quantity);
      }
      await this.loadCart();
    },
    async removeFromCart(productId, skuId) {
      await removeCartItemApi(productId, skuId);
      await this.loadCart();
    },
    async clearCart() {
      // 一项项删（接口无清空）
      for (const i of [...this.items]) {
        await removeCartItemApi(i.productId, i.skuId);
      }
      this.items = [];
      this.selectedIds = [];
      this.selectedCoupon = null;
    },
    // === F-U-034 多选 ===
    async toggleSelect(itemId) {
      const set = new Set(this.selectedIds);
      if (set.has(itemId)) set.delete(itemId); else set.add(itemId);
      this.selectedIds = [...set];
      // 同步后端
      const target = this.items.find(i => i.id === itemId);
      if (target) {
        await selectCartItemApi(target.productId, target.skuId, set.has(itemId));
      }
    },
    async toggleSelectAll() {
      const selectAll = !this.isAllSelected;
      await selectAllCartApi(selectAll);
      await this.loadCart();
    },
    clearSelected() {
      this.selectedIds = [];
    },
    /** 结算：仅结算 selectedIds 中的商品 */
    async checkout(addressId, userCouponId = null, payMethod = "WALLET") {
      if (this.selectedIds.length === 0) throw new Error("请先勾选要结算的商品");
      const userStore = useUserStore();
      if (!userStore.user) throw new Error("用户未登录");

      const order = await createOrderApi({
        cartItemIds: this.selectedIds,
        addressId,
        userCouponId,
        payMethod,
      });

      // 删掉已结算的购物车项（前端乐观更新，再回拉）
      this.selectedIds = [];
      await this.loadCart();
      // 把最终金额快照到 order 对象上，方便弹窗显示（后端已用 totalPrice 字段，这里兼容 payAmount/totalAmount）
      order.payAmount = order.totalPrice ?? order.payAmount ?? order.totalAmount ?? 0;
      return order;
    },
  },
});
