import http from "./http";

// ========== 商品评价（F-U-061 ~ F-U-063） ==========

/**
 * 商品评价分页（含统计：好评率 / 平均分 / 总数）
 *
 * @param {number|string} productId
 * @param {{page?: number, size?: number}} [opts]
 * @returns {Promise<{total: number, positiveRate: number, averageRating: number, items: Array}>}
 */
export const fetchProductReviews = async (productId, opts = {}) => {
  const { page = 1, size = 10 } = opts;
  return await http.get(`/review/product/${productId}`, {
    params: { page, size },
  });
};

/**
 * 评价资格预检（点击"我要评价"前调，决定按钮置灰/提示文案）
 *
 * @returns {Promise<{canReview: boolean, reason?: string, existingReviewId?: number}>}
 *   reason: NOT_LOGGED_IN | NOT_PURCHASED | ALREADY_REVIEWED
 */
export const checkReviewEligibility = async (productId) => {
  return await http.get(`/review/eligibility`, {
    params: { productId },
  });
};

/**
 * 提交评价（需登录；后端会再做三道校验：登录 / 已购 / 防重）
 */
export const submitReview = async ({ productId, rating, content }) => {
  return await http.post(`/review`, { productId, rating, content });
};
