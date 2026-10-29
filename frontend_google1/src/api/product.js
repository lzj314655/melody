import http from "./http";

// 公开接口——无需登录

// 商品分类列表
export const fetchCategories = async () => {
  return await http.get("/product/categories");
};

// 商品列表（支持分页、排序、分类筛选、关键词）
export const fetchProducts = async (params = {}) => {
  return await http.get("/product/list", { params });
};

// 商品详情
export const fetchProductById = async (id) => {
  return await http.get(`/product/detail/${id}`);
};

// SKU 列表（按商品 id）
export const fetchSkusByProduct = async (productId) => {
  return await http.get("/sku", { params: { productId } });
};

// 轮播图
export const fetchBanners = async () => {
  return await http.get("/banner/list");
};

// 公告
export const fetchAnnouncements = async () => {
  return await http.get("/announcement/list");
};

// ========== 后台管理接口（admin 角色） ==========
// 后台商品 CRUD（POST /api/admin/product/...）
export const addProductApi = async (product) => {
  return await http.post("/admin/product/create", product);
};
export const updateProductApi = async (id, patch) => {
  return await http.put(`/admin/product/${id}`, patch);
};
export const deleteProductApi = async (id) => {
  return await http.delete(`/admin/product/${id}`);
};

