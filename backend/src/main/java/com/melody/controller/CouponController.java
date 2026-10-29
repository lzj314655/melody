package com.melody.controller;

import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.entity.TCoupon;
import com.melody.entity.TUserCoupon;
import com.melody.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 优惠券接口（PRD §8.1 /api/coupon）
 */
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final ICouponService couponService;

    /** F-U-060 可领取券 */
    @GetMapping("/available")
    public R<List<TCoupon>> available() {
        return R.ok(couponService.listAvailable());
    }

    /** F-U-060 领取 */
    @PostMapping("/{couponId}/claim")
    public R<TUserCoupon> claim(@PathVariable Long couponId) {
        return R.ok(couponService.claim(SecurityUtils.currentId(), couponId));
    }

    /** F-U-054 我的券（默认仅返可用的；可传 status=ALL 看历史） */
    @GetMapping("/mine")
    public R<List<TUserCoupon>> mine(@RequestParam(required = false, defaultValue = "可用") String status) {
        return R.ok(couponService.myCoupons(SecurityUtils.currentId(), status));
    }
}