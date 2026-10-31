package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.melody.exception.BizException;
import com.melody.common.ResultCode;
import com.melody.entity.TCoupon;
import com.melody.entity.TUserCoupon;
import com.melody.mapper.TCouponMapper;
import com.melody.mapper.TUserCouponMapper;
import com.melody.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements ICouponService {

    private final TCouponMapper couponMapper;
    private final TUserCouponMapper userCouponMapper;

    @Override
    public List<TCoupon> listAvailable() {
        return couponMapper.selectList(new LambdaQueryWrapper<TCoupon>()
                .eq(TCoupon::getStatus, 1)
                .gt(TCoupon::getRemaining, 0)
                .orderByDesc(TCoupon::getDiscount));
    }

    @Override
    public List<TCoupon> listAll() {
        return couponMapper.selectList(new LambdaQueryWrapper<TCoupon>()
                .orderByDesc(TCoupon::getId));
    }

    @Override
    @Transactional
    public TCoupon create(TCoupon c) {
        if (c.getName() == null || c.getName().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "优惠券名称必填");
        }
        if (c.getDiscount() == null || c.getDiscount().signum() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "折扣/面额必须大于 0");
        }
        c.setId(null);
        if (c.getStatus() == null) c.setStatus(1);
        if (c.getRemaining() == null) c.setRemaining(c.getTotal() == null ? 100 : c.getTotal());
        if (c.getTotal() == null) c.setTotal(c.getRemaining());
        couponMapper.insert(c);
        return c;
    }

    @Override
    @Transactional
    public TCoupon update(Long id, TCoupon patch) {
        TCoupon old = couponMapper.selectById(id);
        if (old == null) throw new BizException(ResultCode.NOT_FOUND, "优惠券不存在");
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getDiscount() != null) old.setDiscount(patch.getDiscount());
        if (patch.getMinSpend() != null) old.setMinSpend(patch.getMinSpend());
        if (patch.getTotal() != null) old.setTotal(patch.getTotal());
        if (patch.getRemaining() != null) old.setRemaining(patch.getRemaining());
        if (patch.getStartDate() != null) old.setStartDate(patch.getStartDate());
        if (patch.getEndDate() != null) old.setEndDate(patch.getEndDate());
        if (patch.getDescription() != null) old.setDescription(patch.getDescription());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        if (patch.getType() != null) old.setType(patch.getType());
        couponMapper.updateById(old);
        return old;
    }

    @Override
    @Transactional
    public void disable(Long id) {
        TCoupon c = couponMapper.selectById(id);
        if (c == null) throw new BizException(ResultCode.NOT_FOUND, "优惠券不存在");
        c.setStatus(0);
        couponMapper.updateById(c);
    }

    /** 物理删除优惠券：先清掉所有已领取记录，再删除券本体 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TCoupon c = couponMapper.selectById(id);
        if (c == null) throw new BizException(ResultCode.NOT_FOUND, "优惠券不存在");
        userCouponMapper.delete(new LambdaQueryWrapper<TUserCoupon>().eq(TUserCoupon::getCouponId, id));
        couponMapper.deleteById(id);
    }

    /**
     * 领取优惠券（终身一次）。规则：
     *   - 同一用户对同一 coupon 只要存在任何历史记录（可用/已用/过期）→ 拒绝重复领取
     *   - 否则原子扣减 t_coupon.remaining（WHERE remaining>0）；扣减失败 → 已被领完
     *   - t_user_coupon 唯一索引 uk_user_coupon(user_id, coupon_id) 兜底并发重复插入
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TUserCoupon claim(Long userId, Long couponId) {
        TCoupon c = couponMapper.selectById(couponId);
        if (c == null || c.getStatus() != 1) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券不存在或已下架");
        }

        // 防重：终身一次——无论记录处于可用/已用/过期，都不允许再次领取
        Long existed = userCouponMapper.selectCount(new LambdaQueryWrapper<TUserCoupon>()
                .eq(TUserCoupon::getUserId, userId)
                .eq(TUserCoupon::getCouponId, couponId));
        if (existed != null && existed > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "您已领取过该券，不能重复领取");
        }

        // 校验剩余库存（先 select 再 update，避免 WHERE 失败时静默成功）
        if (c.getRemaining() == null || c.getRemaining() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "券已被领完");
        }
        int affected = couponMapper.update(null, new LambdaUpdateWrapper<TCoupon>()
                .eq(TCoupon::getId, couponId)
                .gt(TCoupon::getRemaining, 0)
                .setSql("remaining = remaining - 1"));
        if (affected == 0) {
            // 并发：被别人抢完了
            throw new BizException(ResultCode.BAD_REQUEST, "券已被领完");
        }

        TUserCoupon uc = new TUserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus("可用");
        uc.setExpireAt(c.getEndDate());
        uc.setClaimedAt(LocalDateTime.now());
        try {
            userCouponMapper.insert(uc);
        } catch (DuplicateKeyException dup) {
            // 兜底：并发双击/重试下唯一索引触发 → 同样按"已领取"处理
            throw new BizException(ResultCode.BAD_REQUEST, "您已领取过该券，不能重复领取");
        }
        return uc;
    }

    @Override
    public List<TUserCoupon> myCoupons(Long userId, String status) {
        LambdaQueryWrapper<TUserCoupon> qw = new LambdaQueryWrapper<TUserCoupon>()
                .eq(TUserCoupon::getUserId, userId)
                .orderByDesc(TUserCoupon::getClaimedAt);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            qw.eq(TUserCoupon::getStatus, status);
        }
        return userCouponMapper.selectList(qw);
    }

    /**
     * 核销优惠券。注意：被传入的 userCouponId 必须是当前 status='可用' 的行；
     * 核销后该券变为"已用"（终身一次，用户不可再领同一张券）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long userId, Long userCouponId, Long orderId) {
        TUserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "券不存在");
        }
        if (!uc.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权核销他人优惠券");
        }
        if (!"可用".equals(uc.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "券不可用");
        }
        if (uc.getExpireAt() != null && uc.getExpireAt().isBefore(LocalDateTime.now())) {
            uc.setStatus("过期");
            userCouponMapper.updateById(uc);
            throw new BizException(ResultCode.BAD_REQUEST, "券已过期");
        }
        // 原子核销：并发用同一张券下两单时，只有一笔能成功
        int affected = userCouponMapper.markUsed(uc.getId(), userId, orderId);
        if (affected != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "券已被使用，请刷新后重试");
        }
        uc.setStatus("已用");
        uc.setUsedAt(LocalDateTime.now());
        uc.setUsedOrderId(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseByOrder(Long userId, Long orderId) {
        userCouponMapper.releaseByOrder(userId, orderId);
    }
}