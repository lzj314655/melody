import { defineStore } from "pinia";
import http from "@/api/http";
import { fetchProducts, fetchCategories, fetchBanners, fetchAnnouncements } from "@/api/product";

// 把后端 TProduct 拍平给前端用
// 必须先在 store 里 loadCategoryMap() 才能拿到 category 名字
function mapProduct(p, categoryMap = {}) {
  if (!p) return null;
  return {
    id: p.id,
    name: p.name,
    description: p.description,
    image: p.coverImage,
    price: Number(p.price || 0),
    marketPrice: Number(p.marketPrice || 0),
    inventory: p.inventory,
    sales: p.sales || 0,
    rating: Number(p.rating || 5.0),
    categoryId: p.categoryId,
    category: categoryMap[p.categoryId] || "其他",
    brand: p.brand,
    origin: p.origin,
    shelfLife: p.shelfLife,
    weight: p.weight,
    ingredients: p.ingredients,
    tags: p.tags || [],
    // 用于卡片"加入购物车"按钮需要 spec
    specs: [p.weight || "默认规格"],
    // 详情页用
    skuList: [],
  };
}

export const useRecommendStore = defineStore("recommend", {
  state: () => ({
    featuredProducts: [],
    categories: [],
    banners: [],
    announcements: [],
    categoryMap: {},   // categoryId → category name
    loading: false,
  }),
  getters: {
    topRatedProducts: (state) =>
      [...state.featuredProducts].sort((a, b) => (b.rating || 0) - (a.rating || 0)).slice(0, 3),
    bestSellers: (state) =>
      [...state.featuredProducts].sort((a, b) => (b.sales || 0) - (a.sales || 0)).slice(0, 4),
  },
  actions: {
    async loadRecommendations() {
      this.loading = true;
      try {
        await this.loadCategoryMap();
        const list = await fetchProducts({ sort: "sales-desc" });
        this.featuredProducts = (list || []).map(p => mapProduct(p, this.categoryMap));
      } catch (err) {
        console.error("Failed to load recommendations", err);
        this.featuredProducts = [];
      } finally {
        this.loading = false;
      }
    },
    async loadPersonalRecommend(limit = 10) {
      const token = localStorage.getItem("access_token");
      if (!token) return null;
      try {
        await this.loadCategoryMap();
        const list = await http.get("/recommend/personal", { params: { limit } });
        return (list || []).map(p => mapProduct(p, this.categoryMap));
      } catch (e) {
        return null;
      }
    },
    /** 冷启动推荐（未登录用户走这个） */
    async loadColdStart(limit = 10) {
      try {
        await this.loadCategoryMap();
        const list = await http.get("/recommend/cold-start", { params: { limit } });
        return (list || []).map(p => mapProduct(p, this.categoryMap));
      } catch (e) {
        return null;
      }
    },
    /** 详情页"猜你喜欢"——基于当前商品找相似 ItemCF */
    async loadSimilar(productId, limit = 6) {
      if (!productId) return [];
      try {
        await this.loadCategoryMap();
        const list = await http.get(`/recommend/similar/${productId}`, { params: { limit } });
        return (list || []).map(p => mapProduct(p, this.categoryMap));
      } catch (e) {
        return [];
      }
    },
    /** 管理员：手动重算 ItemCF 推荐（凌晨 3 点任务的即时触发版本） */
    async refreshRecommendations() {
      try {
        const result = await http.post("/recommend/admin/refresh");
        return { ok: true, message: result?.msg || "推荐已重算", affected: result?.data };
      } catch (e) {
        return { ok: false, message: e.message || "重算失败" };
      }
    },
    async loadCategoryMap() {
      if (Object.keys(this.categoryMap).length) return;
      try {
        const cs = await fetchCategories();
        const map = {};
        (cs || []).forEach(c => { map[c.id] = c.name; });
        this.categoryMap = map;
      } catch (e) {
        console.error(e);
      }
    },
    async loadCategories() {
      try {
        const cs = await fetchCategories();
        const names = (cs || []).map(c => c.name);
        this.categories = ["全部", ...names];
      } catch (e) {
        console.error(e);
        this.categories = ["全部"];
      }
    },
    async loadBanners() {
      try {
        const bs = await fetchBanners();
        this.banners = bs || [];
      } catch (e) { /* ignore */ }
    },
    async loadAnnouncements() {
      try {
        const a = await fetchAnnouncements();
        this.announcements = a || [];
      } catch (e) { /* ignore */ }
    },
  }
});

export { mapProduct };
