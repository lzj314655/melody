import { defineStore } from "pinia";
import {
  fetchUserProfile,
  updateUserProfileApi,
  fetchAddresses,
  addAddressApi,
  updateAddressApi,
  deleteAddressApi,
  fetchCoupons,
  fetchAvailableCoupons,
  claimCouponApi,
  fetchMyFavoritesApi,
  addFavoriteApi,
  removeFavoriteApi,
  loginApi,
  registerApi,
  logoutApi,
} from "@/api/user";
import { setAuthToken } from "@/api/http";

// 把后端返回的 TUser 拍平给前端用
function mapUser(u) {
  if (!u) return null;
  return {
    id: u.id,
    userId: u.id,
    username: u.username,
    nickname: u.nickname,
    avatar: u.avatar,
    phone: u.phone ? String(u.phone).replace(/(\d{3})\d{4}(\d{4})/, "$1****$2") : "",
    rawPhone: u.phone,
    rankCode: u.rankCode,
    rank: u.rankCode === "V3" ? "钻石会员"
        : u.rankCode === "V2" ? "黄金会员"
        : u.rankCode === "V1" ? "银卡会员"
        : "普通会员",
    growthValue: u.growthValue || 0,
    points: u.points || 0,
    balance: 0, // 单独 fetchWallet 后再补
  };
}

export const useUserStore = defineStore("user", {
  state: () => ({
    // F-C-001: token is the source of truth for auth
    token: localStorage.getItem("access_token") || "",
    user: null,
    addresses: [],
    coupons: [],            // 我的券
    availableCoupons: [],   // 可领取券（首页 hero 用）
    favorites: JSON.parse(localStorage.getItem("melody_favorites") || "[]"),
    loading: false,
    error: null,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    defaultAddress: (state) => state.addresses.find(a => a.isDefault) || state.addresses[0],
    availableCouponsList: (state) => state.coupons.filter(c => c.status === "可用" || c.status === "AVAILABLE" || c.status === "UNUSED"),
    isFavorite: (state) => (productId) => state.favorites.includes(productId),
  },
  actions: {
    setToken(token) {
      this.token = token;
      if (token) {
        localStorage.setItem("access_token", token);
        setAuthToken(token);
      } else {
        localStorage.removeItem("access_token");
        setAuthToken(null);
      }
    },
    clearToken() {
      this.setToken("");
    },
    async logout() {
      await logoutApi();
      this.token = "";
      this.user = null;
      this.addresses = [];
      this.coupons = [];
    },
    async loadUser() {
      this.loading = true;
      try {
        const u = await fetchUserProfile();
        this.user = mapUser(u);
      } catch (err) {
        this.error = err.message;
      } finally {
        this.loading = false;
      }
    },
    async loadAddresses() {
      try {
        const list = await fetchAddresses();
        // 统一 isDefault 字段；按 id 倒序稳定排序，避免"设为默认"后卡片跳位导致用户以为没生效
        this.addresses = (list || [])
          .map(a => ({
            ...a,
            isDefault: a.isDefault ?? (a.tag === "DEFAULT" || a.isDefaultFlag === 1),
            // 后端字段名兼容
            id: a.id,
            consignee: a.consignee || a.receiver,
            phone: a.phone || a.receiverPhone,
          }))
          .sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
      } catch (err) {
        this.error = err.message;
      }
    },
    async loadCoupons() {
      try {
        const mine = (await fetchCoupons()) || [];
        // 后端 /coupon/mine 只返 user_coupon（无 name/discount/minSpend），
        // 拉一次 available 作为目录，把字段补齐
        let catalog = [];
        try {
          catalog = (await fetchAvailableCoupons()) || [];
        } catch (e) { catalog = []; }
        const map = new Map(catalog.map(c => [c.id, c]));
        this.coupons = mine.map(uc => {
          const c = map.get(uc.couponId) || {};
          return {
            ...uc,
            id: uc.id,
            couponId: uc.couponId,
            name: c.name || uc.name || '优惠券',
            title: c.name || uc.name,
            type: c.type || uc.type || '',
            discount: c.discount ?? uc.discount ?? 0,
            minSpend: c.minSpend ?? uc.minSpend ?? 0,
            description: c.description || uc.description || '',
            expiryDate: c.endDate || uc.expireAt || '',
            status: uc.status || '可用',
          };
        });
      } catch (err) {
        this.error = err.message;
      }
    },
    async loadAvailableCoupons() {
      try {
        this.availableCoupons = (await fetchAvailableCoupons()) || [];
      } catch (err) {
        this.availableCoupons = [];
      }
    },
    async login(phone, password, captchaId, captchaCode) {
      this.loading = true;
      try {
        const response = await loginApi(phone, password, captchaId, captchaCode);
        const { token, userId, username, nickname, avatar } = response.data;
        this.setToken(token);
        this.user = mapUser({
          id: userId, username, nickname, avatar, phone,
        });
        // 拉侧栏数据
        await Promise.all([
          this.loadAddresses(),
          this.loadCoupons(),
        ]).catch(() => {});
        return this.user;
      } catch (err) {
        this.error = err.message;
        throw err;
      } finally {
        this.loading = false;
      }
    },
    async register(phone, password, captchaId, captchaCode) {
      this.loading = true;
      try {
        const response = await registerApi(phone, password, captchaId, captchaCode);
        const { token, userId, username, nickname, avatar } = response.data;
        this.setToken(token);
        this.user = mapUser({
          id: userId, username, nickname, avatar, phone,
        });
        await Promise.all([
          this.loadAddresses(),
          this.loadCoupons(),
        ]).catch(() => {});
        return this.user;
      } catch (err) {
        this.error = err.message;
        throw err;
      } finally {
        this.loading = false;
      }
    },
    async updateProfile(updatedProfile) {
      const res = await updateUserProfileApi(updatedProfile);
      this.user = { ...this.user, ...mapUser(res) };
      return this.user;
    },
    async addAddress(address) {
      const res = await addAddressApi(address);
      await this.loadAddresses();
      return res;
    },
    async updateAddress(address) {
      // 兼容 number 1 / boolean true 两种 isDefault 写法（防止模板漏判类型导致乐观更新失效）
      const wantDefault = address && (address.isDefault === 1 || address.isDefault === true);
      // 乐观更新：先把本地默认状态切到目标值，避免"默认地址置顶"导致卡片跳位、用户感觉没生效
      const prevState = this.addresses.map(a => ({ id: a.id, isDefault: a.isDefault }));
      if (wantDefault) {
        this.addresses.forEach(a => {
          a.isDefault = a.id === address.id;
        });
      }
      // 强制把请求体的 isDefault 序列化为数字 1，避免 Jackson 反序列化歧义
      const payload = wantDefault ? { ...address, isDefault: 1 } : address;
      try {
        const res = await updateAddressApi(payload);
        // 用后端权威响应覆盖乐观值（处理新建等无 id 场景跳过）
        if (res && res.id != null) {
          const idx = this.addresses.findIndex(a => a.id === res.id);
          if (idx >= 0) {
            const serverDefault = !!(res.isDefault === 1 || res.isDefault === true);
            this.addresses[idx] = {
              ...this.addresses[idx],
              ...res,
              isDefault: serverDefault,
            };
            if (serverDefault) {
              this.addresses.forEach(a => {
                if (a.id !== res.id) a.isDefault = false;
              });
            }
          }
        }
        return res;
      } catch (err) {
        // 回滚乐观更新
        prevState.forEach(p => {
          const a = this.addresses.find(x => x.id === p.id);
          if (a) a.isDefault = p.isDefault;
        });
        throw err;
      }
    },
    async deleteAddress(id) {
      await deleteAddressApi(id);
      await this.loadAddresses();
    },
    async claimCoupon(couponId) {
      await claimCouponApi(couponId);
      await this.loadCoupons();
    },
    toggleFavorite(productId) {
      const token = localStorage.getItem("access_token");
      if (token) {
        const idx = this.favorites.indexOf(productId);
        const willAdd = idx === -1;
        if (willAdd) this.favorites.push(productId);
        else this.favorites.splice(idx, 1);
        localStorage.setItem("melody_favorites", JSON.stringify(this.favorites));
        // 调后端，失败回滚；登录态失效时弹 toast 引导用户
        const p = willAdd
          ? addFavoriteApi(productId)
          : removeFavoriteApi(productId);
        p.catch((e) => {
          // 回滚 UI
          if (willAdd) {
            const i = this.favorites.indexOf(productId);
            if (i >= 0) this.favorites.splice(i, 1);
          } else {
            if (!this.favorites.includes(productId)) this.favorites.push(productId);
          }
          localStorage.setItem("melody_favorites", JSON.stringify(this.favorites));
          // 401/1002 = 登录态失效 → 跳登录页；其它静默
          if (e && (e.code === 401 || e.code === 1002)) {
            localStorage.removeItem("access_token");
            alert("登录已过期，请重新登录后再收藏");
            if (typeof window !== "undefined") window.location.href = "/login";
          }
        });
        return willAdd;
      }
      // 未登录降级：仅 localStorage
      const idx = this.favorites.indexOf(productId);
      if (idx === -1) {
        this.favorites.push(productId);
      } else {
        this.favorites.splice(idx, 1);
      }
      localStorage.setItem("melody_favorites", JSON.stringify(this.favorites));
      return idx === -1;
    },
    async loadFavoritesFromServer() {
      try {
        const ids = await fetchMyFavoritesApi();
        if (Array.isArray(ids)) {
          this.favorites = ids;
          localStorage.setItem("melody_favorites", JSON.stringify(this.favorites));
        }
      } catch (e) { /* 未登录忽略 */ }
    }
  }
});
