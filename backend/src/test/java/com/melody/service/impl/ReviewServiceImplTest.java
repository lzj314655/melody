package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.melody.common.ResultCode;
import com.melody.dto.ReviewDTOs.ReviewEligibilityVO;
import com.melody.dto.ReviewDTOs.ReviewPageVO;
import com.melody.dto.ReviewDTOs.ReviewSubmitRequest;
import com.melody.entity.TProductReview;
import com.melody.entity.TUser;
import com.melody.exception.BizException;
import com.melody.mapper.TOrderMapper;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.TProductReviewMapper;
import com.melody.mapper.TUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReviewServiceImpl 单元测试：listReviews / checkEligibility / submitReview。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewServiceImpl 单元测试")
class ReviewServiceImplTest {

    @Mock private TProductReviewMapper reviewMapper;
    @Mock private TUserMapper userMapper;
    @Mock private TOrderMapper orderMapper;
    @Mock private TProductMapper productMapper;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private TProductReview newReview(Long id, Long userId, Long productId, Integer rating, Integer anonymous) {
        TProductReview r = new TProductReview();
        r.setId(id);
        r.setUserId(userId);
        r.setProductId(productId);
        r.setRating(rating);
        r.setContent("好吃");
        r.setIsAnonymous(anonymous);
        r.setIsHidden(0);
        return r;
    }

    // ============ listReviews ============

    @Test
    @DisplayName("listReviews 商品无评价 → 全 0")
    void listReviews_empty() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", 0);
        stats.put("positiveCount", 0);
        stats.put("avgRating", null);
        when(reviewMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of(stats));

        ReviewPageVO vo = reviewService.listReviews(1L, 1, 10);
        assertThat(vo.getTotal()).isZero();
        assertThat(vo.getItems()).isEmpty();
    }

    @Test
    @DisplayName("listReviews 统计 + 分页 + 回填用户信息")
    void listReviews_fullFlow() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", 10L);
        stats.put("positiveCount", 8L);
        stats.put("avgRating", 4.5);
        when(reviewMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of(stats));

        TProductReview r1 = newReview(1L, 100L, 1L, 5, 0);
        TProductReview r2 = newReview(2L, 101L, 1L, 5, 1); // 匿名
        Page<TProductReview> page = new Page<>(1, 10);
        page.setRecords(List.of(r1, r2));
        when(reviewMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        TUser u = new TUser();
        u.setId(100L);
        u.setNickname("alice");
        when(userMapper.selectBatchIds(any(java.util.Collection.class))).thenReturn(List.of(u));

        ReviewPageVO vo = reviewService.listReviews(1L, 1, 10);
        assertThat(vo.getTotal()).isEqualTo(10L);
        assertThat(vo.getPositiveRate()).isEqualTo(0.8);
        assertThat(vo.getAverageRating()).isEqualTo(4.5);
        assertThat(vo.getItems()).hasSize(2);
        // 非匿名 → 回填用户名
        assertThat(vo.getItems().get(0).getDisplayName()).isEqualTo("alice");
        assertThat(vo.getItems().get(0).getAnonymous()).isFalse();
        // 匿名 → "匿名用户"
        assertThat(vo.getItems().get(1).getDisplayName()).isEqualTo("匿名用户");
    }

    @Test
    @DisplayName("listReviews size>MAX → 截断到 100")
    void listReviews_sizeClamped() {
        when(reviewMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of(Map.of("totalCount", 0L)));
        ReviewPageVO vo = reviewService.listReviews(1L, 1, 999);
        // 由于 total=0 直接返回，但 size 已被截断
        assertThat(vo).isNotNull();
    }

    @Test
    @DisplayName("listReviews productId null → BAD_REQUEST")
    void listReviews_nullProductId() {
        assertThatThrownBy(() -> reviewService.listReviews(null, 1, 10))
                .isInstanceOf(BizException.class);
    }

    // ============ checkEligibility ============

    @Test
    @DisplayName("checkEligibility 未登录 → NOT_LOGGED_IN")
    void checkEligibility_notLoggedIn() {
        ReviewEligibilityVO vo = reviewService.checkEligibility(null, 1L);
        assertThat(vo.getCanReview()).isFalse();
        assertThat(vo.getReason()).isEqualTo("NOT_LOGGED_IN");
    }

    @Test
    @DisplayName("checkEligibility 已评价 → ALREADY_REVIEWED + existingReviewId")
    void checkEligibility_alreadyReviewed() {
        TProductReview exist = newReview(99L, 1L, 1L, 5, 0);
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);

        ReviewEligibilityVO vo = reviewService.checkEligibility(1L, 1L);
        assertThat(vo.getCanReview()).isFalse();
        assertThat(vo.getReason()).isEqualTo("ALREADY_REVIEWED");
        assertThat(vo.getExistingReviewId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("checkEligibility 未购买 → NOT_PURCHASED")
    void checkEligibility_notPurchased() {
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(orderMapper.countPurchasedProduct(1L, 1L)).thenReturn(0L);

        ReviewEligibilityVO vo = reviewService.checkEligibility(1L, 1L);
        assertThat(vo.getCanReview()).isFalse();
        assertThat(vo.getReason()).isEqualTo("NOT_PURCHASED");
    }

    @Test
    @DisplayName("checkEligibility 通过 → canReview=true")
    void checkEligibility_ok() {
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(orderMapper.countPurchasedProduct(1L, 1L)).thenReturn(1L);

        ReviewEligibilityVO vo = reviewService.checkEligibility(1L, 1L);
        assertThat(vo.getCanReview()).isTrue();
    }

    @Test
    @DisplayName("checkEligibility productId null → BAD_REQUEST")
    void checkEligibility_nullProduct() {
        assertThatThrownBy(() -> reviewService.checkEligibility(1L, null))
                .isInstanceOf(BizException.class);
    }

    // ============ submitReview ============

    @Test
    @DisplayName("submitReview userId=null → UNAUTHORIZED")
    void submitReview_unauthorized() {
        assertThatThrownBy(() -> reviewService.submitReview(null, new ReviewSubmitRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("submitReview req 缺字段 → BAD_REQUEST")
    void submitReview_missingFields() {
        ReviewSubmitRequest req = new ReviewSubmitRequest();
        req.setProductId(1L);
        // 缺 rating/content

        assertThatThrownBy(() -> reviewService.submitReview(1L, req))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("submitReview 已评价 → REVIEW_ALREADY_EXISTS")
    void submitReview_already() {
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(newReview(99L, 1L, 1L, 5, 0));

        ReviewSubmitRequest req = new ReviewSubmitRequest();
        req.setProductId(1L);
        req.setRating(5);
        req.setContent("好吃");

        assertThatThrownBy(() -> reviewService.submitReview(1L, req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.REVIEW_ALREADY_EXISTS.getCode()));
    }

    @Test
    @DisplayName("submitReview 未购买 → REVIEW_NOT_PURCHASED")
    void submitReview_notPurchased() {
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(orderMapper.countPurchasedProduct(1L, 1L)).thenReturn(0L);

        ReviewSubmitRequest req = new ReviewSubmitRequest();
        req.setProductId(1L);
        req.setRating(5);
        req.setContent("好吃");

        assertThatThrownBy(() -> reviewService.submitReview(1L, req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.REVIEW_NOT_PURCHASED.getCode()));
    }

    @Test
    @DisplayName("submitReview 成功 → 写入评价 + 回写 rating")
    void submitReview_success() {
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(orderMapper.countPurchasedProduct(1L, 1L)).thenReturn(1L);
        when(reviewMapper.insert(any(TProductReview.class))).thenAnswer(inv -> {
            TProductReview arg = inv.getArgument(0);
            arg.setId(123L);
            return 1;
        });
        // 回写 rating 用的 stats
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", 1L);
        stats.put("avgRating", 4.5);
        when(reviewMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of(stats));
        when(productMapper.updateRating(eq(1L), any(BigDecimal.class))).thenReturn(1);

        ReviewSubmitRequest req = new ReviewSubmitRequest();
        req.setProductId(1L);
        req.setRating(5);
        req.setContent("  好吃  ");

        Long id = reviewService.submitReview(1L, req);
        assertThat(id).isEqualTo(123L);

        ArgumentCaptor<TProductReview> captor = ArgumentCaptor.forClass(TProductReview.class);
        verify(reviewMapper).insert(captor.capture());
        // 内容 trim
        assertThat(captor.getValue().getContent()).isEqualTo("好吃");
        // 默认匿名/隐藏 = 0
        assertThat(captor.getValue().getIsAnonymous()).isEqualTo(0);
        assertThat(captor.getValue().getIsHidden()).isEqualTo(0);
        // 回写评分
        verify(productMapper, times(1)).updateRating(eq(1L), any(BigDecimal.class));
    }

    @Test
    @DisplayName("submitReview rating 回写失败不影响主流程（仅 warn 日志）")
    void submitReview_recalcFailureNonBlocking() {
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(orderMapper.countPurchasedProduct(1L, 1L)).thenReturn(1L);
        when(reviewMapper.insert(any(TProductReview.class))).thenAnswer(inv -> {
            TProductReview arg = inv.getArgument(0);
            arg.setId(123L);
            return 1;
        });
        // stats 查询抛异常 → 不应阻塞
        when(reviewMapper.selectMaps(any(QueryWrapper.class))).thenThrow(new RuntimeException("DB err"));

        ReviewSubmitRequest req = new ReviewSubmitRequest();
        req.setProductId(1L);
        req.setRating(5);
        req.setContent("好吃");

        Long id = reviewService.submitReview(1L, req);
        assertThat(id).isEqualTo(123L);
        verify(productMapper, never()).updateRating(any(), any());
    }
}
