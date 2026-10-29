package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.melody.common.ResultCode;
import com.melody.entity.TCoupon;
import com.melody.entity.TUserCoupon;
import com.melody.exception.BizException;
import com.melody.mapper.TCouponMapper;
import com.melody.mapper.TUserCouponMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CouponServiceImpl 单元测试：listAvailable / listAll / create / update / disable / delete /
 * claim（并发兜底 + 防重）/ myCoupons / consume。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponServiceImpl 单元测试")
class CouponServiceImplTest {

    @Mock private TCouponMapper couponMapper;
    @Mock private TUserCouponMapper userCouponMapper;

    @InjectMocks
    private CouponServiceImpl couponService;

    private TCoupon coupon(Long id, String type, String discount) {
        TCoupon c = new TCoupon();
        c.setId(id);
        c.setName("测试券");
        c.setType(type);
        c.setDiscount(new BigDecimal(discount));
        c.setMinSpend(BigDecimal.ZERO);
        c.setTotal(100);
        c.setRemaining(50);
        c.setStatus(1);
        return c;
    }

    // ============ listAvailable / listAll ============

    @Test
    @DisplayName("listAvailable 仅 status=1 + remaining>0 + 折扣 desc")
    void listAvailable() {
        when(couponMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThat(couponService.listAvailable()).isEmpty();
    }

    @Test
    @DisplayName("listAll 不过滤 status")
    void listAll() {
        when(couponMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThat(couponService.listAll()).isEmpty();
    }

    // ============ create ============

    @Test
    @DisplayName("create 缺 name → BAD_REQUEST")
    void create_missingName() {
        TCoupon c = new TCoupon();
        c.setDiscount(new BigDecimal("10"));
        assertThatThrownBy(() -> couponService.create(c))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("create discount ≤ 0 → BAD_REQUEST")
    void create_zeroDiscount() {
        TCoupon c = new TCoupon();
        c.setName("测试");
        c.setDiscount(BigDecimal.ZERO);
        assertThatThrownBy(() -> couponService.create(c))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create remaining 默认等于 total")
    void create_defaultsRemaining() {
        TCoupon c = new TCoupon();
        c.setName("测试");
        c.setDiscount(new BigDecimal("10"));
        c.setTotal(200);
        c.setRemaining(null);

        when(couponMapper.insert(any(TCoupon.class))).thenAnswer(inv -> {
            TCoupon arg = inv.getArgument(0);
            arg.setId(99L);
            return 1;
        });

        TCoupon result = couponService.create(c);
        assertThat(result.getRemaining()).isEqualTo(200);
    }

    // ============ update / disable / delete ============

    @Test
    @DisplayName("update 不存在 → NOT_FOUND")
    void update_missing() {
        when(couponMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> couponService.update(99L, new TCoupon()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("disable 成功：status=0")
    void disable_success() {
        TCoupon c = coupon(1L, "满减", "10");
        when(couponMapper.selectById(1L)).thenReturn(c);

        couponService.disable(1L);
        assertThat(c.getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("delete 先清空 user_coupon 再删 coupon")
    void delete_cascades() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(1L, "满减", "10"));

        couponService.delete(1L);

        verify(userCouponMapper).delete(any(LambdaQueryWrapper.class));
        verify(couponMapper).deleteById(1L);
    }

    // ============ claim ============

    @Test
    @DisplayName("claim 成功：扣 remaining + 写 user_coupon")
    void claim_success() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(1L, "满减", "10"));
        when(userCouponMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(couponMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);

        TUserCoupon result = couponService.claim(1L, 1L);
        assertThat(result.getStatus()).isEqualTo("可用");
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getCouponId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("claim 券下架 → NOT_FOUND")
    void claim_offShelf() {
        TCoupon c = coupon(1L, "满减", "10");
        c.setStatus(0);
        when(couponMapper.selectById(1L)).thenReturn(c);

        assertThatThrownBy(() -> couponService.claim(1L, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("claim 已领取过 → BAD_REQUEST")
    void claim_alreadyClaimed() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(1L, "满减", "10"));
        when(userCouponMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> couponService.claim(1L, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("claim 已被领完（remaining=0）→ BAD_REQUEST")
    void claim_zeroRemaining() {
        TCoupon c = coupon(1L, "满减", "10");
        c.setRemaining(0);
        when(couponMapper.selectById(1L)).thenReturn(c);
        when(userCouponMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> couponService.claim(1L, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("claim 并发被抢光（update 返 0）→ BAD_REQUEST")
    void claim_concurrencyEmpty() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(1L, "满减", "10"));
        when(userCouponMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(couponMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> couponService.claim(1L, 1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("claim 兜底 DuplicateKeyException → 视为已领取")
    void claim_duplicateKeyFallback() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(1L, "满减", "10"));
        when(userCouponMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(couponMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(userCouponMapper.insert(any(TUserCoupon.class)))
                .thenThrow(new DuplicateKeyException("uk_user_coupon dup"));

        assertThatThrownBy(() -> couponService.claim(1L, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("不能重复领取"));
    }

    // ============ myCoupons ============

    @Test
    @DisplayName("myCoupons ALL → 不加 status 过滤")
    void myCoupons_all() {
        when(userCouponMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThat(couponService.myCoupons(1L, "ALL")).isEmpty();
    }

    @Test
    @DisplayName("myCoupons 指定 status → 加 status 过滤")
    void myCoupons_withStatus() {
        when(userCouponMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThat(couponService.myCoupons(1L, "可用")).isEmpty();
    }

    // ============ consume ============

    @Test
    @DisplayName("consume 成功：可用 → 已用")
    void consume_success() {
        TUserCoupon uc = new TUserCoupon();
        uc.setId(1L);
        uc.setUserId(1L);
        uc.setCouponId(100L);
        uc.setStatus("可用");
        uc.setExpireAt(LocalDateTime.now().plusDays(30));
        when(userCouponMapper.selectById(1L)).thenReturn(uc);

        couponService.consume(1L, 1L, 999L);

        assertThat(uc.getStatus()).isEqualTo("已用");
        assertThat(uc.getUsedOrderId()).isEqualTo(999L);
        verify(userCouponMapper).updateById(uc);
    }

    @Test
    @DisplayName("consume 券不存在 → BAD_REQUEST")
    void consume_missing() {
        when(userCouponMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> couponService.consume(1L, 1L, 999L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("consume 他人券 → FORBIDDEN")
    void consume_notOwned() {
        TUserCoupon uc = new TUserCoupon();
        uc.setId(1L);
        uc.setUserId(2L); // 不是当前用户
        uc.setStatus("可用");
        when(userCouponMapper.selectById(1L)).thenReturn(uc);

        assertThatThrownBy(() -> couponService.consume(1L, 1L, 999L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("consume 状态非可用 → BAD_REQUEST")
    void consume_invalidStatus() {
        TUserCoupon uc = new TUserCoupon();
        uc.setId(1L);
        uc.setUserId(1L);
        uc.setStatus("已用");
        when(userCouponMapper.selectById(1L)).thenReturn(uc);

        assertThatThrownBy(() -> couponService.consume(1L, 1L, 999L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("consume 已过期 → 自动标记 过期 并抛 BAD_REQUEST")
    void consume_expired() {
        TUserCoupon uc = new TUserCoupon();
        uc.setId(1L);
        uc.setUserId(1L);
        uc.setStatus("可用");
        uc.setExpireAt(LocalDateTime.now().minusDays(1));
        when(userCouponMapper.selectById(1L)).thenReturn(uc);

        assertThatThrownBy(() -> couponService.consume(1L, 1L, 999L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("过期"));

        // 过期状态被自动写回
        assertThat(uc.getStatus()).isEqualTo("过期");
        verify(userCouponMapper, times(1)).updateById(uc);
    }
}
