package com.melody.service.impl;

import com.melody.entity.TProduct;
import com.melody.entity.UserBehaviorLog;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.UserBehaviorLogMapper;
import com.melody.service.IProductService;
import com.melody.service.recommend.ItemCFEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RecommendServiceImpl 单元测试 — 验证 Redis 缓存命中 / 实时算 / 分类降级 / 冷启动四层降级。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendServiceImpl 单元测试")
class RecommendServiceImplTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private IProductService productService;
    @Mock private TProductMapper productMapper;
    @Mock private UserBehaviorLogMapper behaviorLogMapper;

    @InjectMocks
    private RecommendServiceImpl recommendService;

    private TProduct product(Long id, Long categoryId) {
        TProduct p = new TProduct();
        p.setId(id);
        p.setName("p" + id);
        p.setCategoryId(categoryId);
        p.setStatus(1);
        p.setPrice(new BigDecimal("9.90"));
        p.setRating(new BigDecimal("4.5"));
        p.setSales(10);
        return p;
    }

    private UserBehaviorLog log(Long uid, Long pid, String type) {
        UserBehaviorLog l = new UserBehaviorLog();
        l.setUserId(uid);
        l.setProductId(pid);
        l.setBehaviorType(type);
        return l;
    }

    // ============ personalRecommend ============

    @Test
    @DisplayName("personalRecommend null userId → 冷启动")
    void personalRecommend_anonymous() {
        List<TProduct> top = List.of(product(1L, 1L), product(2L, 1L));
        when(productService.list(null, null, "rating-desc")).thenReturn(top);

        List<Long> result = recommendService.personalRecommend(null, 10);
        assertThat(result).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("personalRecommend Redis 命中 → 直接返回缓存")
    void personalRecommend_cacheHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rec:user:1")).thenReturn(List.of(100L, 200L, 300L));

        List<Long> result = recommendService.personalRecommend(1L, 2);
        assertThat(result).containsExactly(100L, 200L);
        verify(productService, never()).list(any(), any(), any());
    }

    @Test
    @DisplayName("personalRecommend Redis miss + 实时算命中 → 写回 Redis 1h")
    void personalRecommend_onTheFlyHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null); // miss
        // 设计：user1 看 100；user2 看 100, 101；user3 看 101, 102
        // → 100 与 101 共现，user1 应被推荐 101
        when(behaviorLogMapper.selectList(any())).thenReturn(List.of(
                log(1L, 100L, "VIEW"),
                log(2L, 100L, "VIEW"),
                log(2L, 101L, "VIEW"),
                log(3L, 101L, "VIEW"),
                log(3L, 102L, "VIEW")
        ));

        List<Long> result = recommendService.personalRecommend(1L, 10);
        // 实时算出推荐 → 写回缓存
        assertThat(result).isNotEmpty();
        verify(valueOps).set(eq("rec:user:1"), any(), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("personalRecommend 无任何数据 → coldStart")
    void personalRecommend_coldStartFallback() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(behaviorLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(productService.list(null, null, "rating-desc")).thenReturn(List.of(product(1L, 1L)));

        List<Long> result = recommendService.personalRecommend(999L, 10);
        assertThat(result).containsExactly(1L);
    }

    // ============ similarRecommend ============

    @Test
    @DisplayName("similarRecommend Redis 命中 → 直接返回")
    void similarRecommend_cacheHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rec:item:100")).thenReturn(List.of(200L, 300L));

        List<Long> result = recommendService.similarRecommend(100L, 10);
        assertThat(result).containsExactly(200L, 300L);
    }

    @Test
    @DisplayName("similarRecommend 无缓存 → 同分类销量降级")
    void similarRecommend_sameCategoryFallback() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(productService.getDetail(anyLong())).thenReturn(product(100L, 1L));
        // 用 any() 匹配所有参数，包括 null keyword
        when(productService.list(nullable(Long.class), nullable(String.class), anyString()))
                .thenReturn(List.of(product(200L, 1L), product(300L, 1L)));

        List<Long> result = recommendService.similarRecommend(100L, 2);
        // 同分类返回的 200/300 都在
        assertThat(result).containsExactlyInAnyOrder(200L, 300L);
    }

    @Test
    @DisplayName("similarRecommend null productId → 冷启动")
    void similarRecommend_nullProductId() {
        when(productService.list(any(), any(), eq("rating-desc")))
                .thenReturn(List.of(product(1L, 1L)));

        List<Long> result = recommendService.similarRecommend(null, 10);
        assertThat(result).containsExactly(1L);
    }

    // ============ coldStart ============

    @Test
    @DisplayName("coldStart 按 rating-desc 取 limit")
    void coldStart() {
        when(productService.list(null, null, "rating-desc"))
                .thenReturn(List.of(product(1L, 1L), product(2L, 1L), product(3L, 1L)));

        List<Long> result = recommendService.coldStart(2);
        assertThat(result).containsExactly(1L, 2L);
    }

    // ============ hasCachedPersonalRec ============

    @Test
    @DisplayName("hasCachedPersonalRec 命中 → true")
    void hasCached_true() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rec:user:1")).thenReturn(List.of(100L));
        assertThat(recommendService.hasCachedPersonalRec(1L)).isTrue();
    }

    @Test
    @DisplayName("hasCachedPersonalRec 未命中 → false")
    void hasCached_false() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        assertThat(recommendService.hasCachedPersonalRec(1L)).isFalse();
    }

    @Test
    @DisplayName("hasCachedPersonalRec null userId → false")
    void hasCached_nullUser() {
        assertThat(recommendService.hasCachedPersonalRec(null)).isFalse();
    }
}
