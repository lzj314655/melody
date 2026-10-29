package com.melody.controller;

import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.dto.ReviewDTOs.ReviewEligibilityVO;
import com.melody.dto.ReviewDTOs.ReviewPageVO;
import com.melody.dto.ReviewDTOs.ReviewSubmitRequest;
import com.melody.service.IReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价接口（F-U-061 ~ F-U-063）
 *
 * 路径前缀：/api/review
 */
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final IReviewService reviewService;

    /** 商品评价分页（公开，未隐藏的评价） */
    @GetMapping("/product/{productId}")
    public R<ReviewPageVO> listByProduct(
            @org.springframework.web.bind.annotation.PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(reviewService.listReviews(productId, page, size));
    }

    /** 评价资格预检（登录态下才返回真实原因；未登录也允许调，返回 NOT_LOGGED_IN） */
    @GetMapping("/eligibility")
    public R<ReviewEligibilityVO> eligibility(@RequestParam Long productId) {
        return R.ok(reviewService.checkEligibility(SecurityUtils.currentId(), productId));
    }

    /** 提交评价（需登录，内部三道校验：登录 / 已购 / 防重） */
    @PostMapping
    public R<Long> submit(@RequestBody @Valid ReviewSubmitRequest req) {
        Long userId = SecurityUtils.currentId();
        if (userId == null) return R.<Long>fail(com.melody.common.ResultCode.UNAUTHORIZED);
        Long reviewId = reviewService.submitReview(userId, req);
        return R.ok("评价成功", reviewId);
    }
}
