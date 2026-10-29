package com.melody.service;

import java.util.List;

/**
 * 推荐 Service（F-R-005 / F-R-006 / F-R-004）
 */
public interface IRecommendService {

    /** F-R-005 首页"为你推荐" */
    List<Long> personalRecommend(Long userId, int limit);

    /** F-R-006 详情页"猜你喜欢" */
    List<Long> similarRecommend(Long productId, int limit);

    /** F-R-004 冷启动推荐（无行为 / 新用户 / 新商品） */
    List<Long> coldStart(int limit);

    /** 调试用：检查某用户的个性化推荐是否已缓存 */
    boolean hasCachedPersonalRec(Long userId);
}