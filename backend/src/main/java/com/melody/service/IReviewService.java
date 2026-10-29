package com.melody.service;

import com.melody.dto.ReviewDTOs.ReviewEligibilityVO;
import com.melody.dto.ReviewDTOs.ReviewPageVO;
import com.melody.dto.ReviewDTOs.ReviewSubmitRequest;

/**
 * 商品评价 Service
 */
public interface IReviewService {

    /**
     * 商品评价分页查询（含统计：好评率、平均分）
     *
     * @param productId 商品 ID
     * @param page      1-based
     * @param size      每页条数（1-100，越界归一化）
     */
    ReviewPageVO listReviews(Long productId, int page, int size);

    /**
     * 评价资格预检（点击"我要评价"时调，按钮置灰/弹窗前置判断）
     *
     * @param userId    当前登录用户 ID；为 null 表示未登录
     * @param productId 商品 ID
     */
    ReviewEligibilityVO checkEligibility(Long userId, Long productId);

    /**
     * 提交评价（内部会再次校验：登录态 / 已购 / 防重）
     *
     * @return 新建评价 ID
     * @throws com.melody.exception.BizException 三种拒绝场景
     */
    Long submitReview(Long userId, ReviewSubmitRequest req);
}
