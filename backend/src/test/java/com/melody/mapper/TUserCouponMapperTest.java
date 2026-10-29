package com.melody.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.TUserCoupon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TUserCouponMapper 测试：CRUD + uk_user_coupon(user_id, coupon_id) 唯一约束。
 *
 * <p>CouponServiceImpl.claim() 依赖此唯一约束做并发兜底。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("TUserCouponMapper 集成测试")
class TUserCouponMapperTest {

    @Autowired
    private TUserCouponMapper userCouponMapper;

    @BeforeEach
    void cleanTable() {
        userCouponMapper.delete(new LambdaQueryWrapper<>());
    }

    private TUserCoupon seed(Long userId, Long couponId, String status) {
        TUserCoupon uc = new TUserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus(status);
        uc.setExpireAt(LocalDateTime.now().plusDays(30));
        uc.setClaimedAt(LocalDateTime.now());
        userCouponMapper.insert(uc);
        return uc;
    }

    @Test
    @DisplayName("insert + selectById")
    void insertAndSelect() {
        TUserCoupon uc = seed(1L, 100L, "可用");
        assertThat(uc.getId()).isNotNull();
        TUserCoupon loaded = userCouponMapper.selectById(uc.getId());
        assertThat(loaded.getUserId()).isEqualTo(1L);
        assertThat(loaded.getStatus()).isEqualTo("可用");
    }

    @Test
    @DisplayName("uk_user_coupon 唯一约束 — 同 user+coupon 重复插入抛 DuplicateKey")
    void uniqueConstraint() {
        seed(1L, 100L, "可用");
        assertThatThrownBy(() -> seed(1L, 100L, "可用"))
                .satisfiesAnyOf(
                        ex -> assertThat(ex).hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class),
                        ex -> assertThat(ex).hasRootCauseInstanceOf(org.springframework.dao.DuplicateKeyException.class),
                        ex -> assertThat(ex).hasMessageContaining("uk_user_coupon"));
    }

    @Test
    @DisplayName("不同 user + 同一 coupon 不冲突")
    void differentUserSameCoupon_allowed() {
        seed(1L, 100L, "可用");
        TUserCoupon uc2 = seed(2L, 100L, "可用");
        assertThat(uc2.getId()).isNotNull();
        assertThat(userCouponMapper.selectCount(new LambdaQueryWrapper<>())).isEqualTo(2L);
    }

    @Test
    @DisplayName("updateById 状态流转：可用 → 已用")
    void consumeStatus() {
        TUserCoupon uc = seed(1L, 100L, "可用");
        uc.setStatus("已用");
        uc.setUsedAt(LocalDateTime.now());
        uc.setUsedOrderId(999L);
        int affected = userCouponMapper.updateById(uc);
        assertThat(affected).isEqualTo(1);

        TUserCoupon loaded = userCouponMapper.selectById(uc.getId());
        assertThat(loaded.getStatus()).isEqualTo("已用");
        assertThat(loaded.getUsedOrderId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("按 user_id + status 过滤 — service.myCoupons 行为")
    void selectByUserAndStatus() {
        seed(1L, 100L, "可用");
        seed(1L, 101L, "可用");
        seed(1L, 102L, "已用");
        seed(2L, 100L, "可用");

        List<TUserCoupon> available = userCouponMapper.selectList(
                new LambdaQueryWrapper<TUserCoupon>()
                        .eq(TUserCoupon::getUserId, 1L)
                        .eq(TUserCoupon::getStatus, "可用"));
        assertThat(available).hasSize(2);

        List<TUserCoupon> used = userCouponMapper.selectList(
                new LambdaQueryWrapper<TUserCoupon>()
                        .eq(TUserCoupon::getUserId, 1L)
                        .eq(TUserCoupon::getStatus, "已用"));
        assertThat(used).hasSize(1);
    }

    @Test
    @DisplayName("delete by couponId — service.delete 清空用户领取记录")
    void deleteByCouponId() {
        seed(1L, 200L, "可用");
        seed(2L, 200L, "可用");
        seed(3L, 201L, "可用"); // 其他 coupon 不应被删

        int affected = userCouponMapper.delete(
                new LambdaQueryWrapper<TUserCoupon>().eq(TUserCoupon::getCouponId, 200L));
        assertThat(affected).isEqualTo(2);

        assertThat(userCouponMapper.selectCount(
                new LambdaQueryWrapper<TUserCoupon>().eq(TUserCoupon::getCouponId, 200L))).isZero();
        assertThat(userCouponMapper.selectCount(
                new LambdaQueryWrapper<TUserCoupon>().eq(TUserCoupon::getCouponId, 201L))).isEqualTo(1L);
    }
}
