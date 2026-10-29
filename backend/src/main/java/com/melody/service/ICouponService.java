package com.melody.service;

import com.melody.entity.TCoupon;
import com.melody.entity.TUserCoupon;

import java.util.List;

/**
 * 优惠券 Service（PRD §7.4）
 */
public interface ICouponService {

    /** 全部可领取的券（含已上架但 remaining<=0；后台列表用） */
    List<TCoupon> listAll();

    /** 前台用户可领取的券（status=1 且 remaining>0） */
    List<TCoupon> listAvailable();

    /** F-U-060 领取 */
    TUserCoupon claim(Long userId, Long couponId);

    /** F-U-054 我的券 */
    List<TUserCoupon> myCoupons(Long userId, String status);

    /** 核销（下单成功后调用） */
    void consume(Long userId, Long userCouponId, Long orderId);

    /** 后台：创建优惠券 */
    TCoupon create(TCoupon c);

    /** 后台：停用（下架）优惠券 */
    void disable(Long id);

    /** 后台：物理删除优惠券（同时清掉已领取记录） */
    void delete(Long id);

    /** 后台：更新优惠券 */
    TCoupon update(Long id, TCoupon patch);
}