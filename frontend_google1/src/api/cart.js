import http from "./http";

// 后端约定：/api/cart 为当前用户购物车
// 字段：productId, skuId, quantity, selected

// 拉购物车
export const getCartItemsApi = async () => {
  return await http.get("/cart");
};

// 新增（productId+skuId 已存在则自增）
export const addCartItemApi = async (productId, skuId, quantity = 1) => {
  return await http.post("/cart", { productId, skuId, quantity });
};

// 修改数量——后端用 @RequestParam，所以传 params 而非 body
export const updateCartItemApi = async (productId, skuId, quantity) => {
  return await http.put(`/cart/${productId}/${skuId}`, null, { params: { quantity } });
};

// 删除一项
export const removeCartItemApi = async (productId, skuId) => {
  return await http.delete(`/cart/${productId}/${skuId}`);
};

// 切换某项选中
export const selectCartItemApi = async (productId, skuId, selected) => {
  return await http.put(`/cart/${productId}/${skuId}/select`, { selected });
};

// 全选 / 全不选
export const selectAllCartApi = async (selected) => {
  return await http.put("/cart/select-all", { selected });
};
