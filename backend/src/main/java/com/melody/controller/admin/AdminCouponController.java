package com.melody.controller.admin;

import com.melody.common.R;
import com.melody.entity.TCoupon;
import com.melody.entity.TPromotion;
import com.melody.service.ICouponService;
import com.melody.service.impl.OperationLogServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台营销管理（PRD §8.2 /api/admin/coupon + /api/admin/promotion，F-A-050 ~ F-A-054）
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
@RequiredArgsConstructor
public class AdminCouponController {

    private final ICouponService couponService;
    private final OperationLogServiceImpl opLog;

    /** F-A-050 全部优惠券（前后台列表用） */
    @GetMapping("/coupon/list")
    @PreAuthorize("hasAuthority('coupon:manage')")
    public R<List<TCoupon>> coupons() {
        return R.ok(couponService.listAll());
    }

    /** F-A-051 创建优惠券 */
    @PostMapping("/coupon/create")
    @PreAuthorize("hasAuthority('coupon:manage')")
    public R<TCoupon> create(@RequestBody TCoupon coupon) {
        TCoupon saved = couponService.create(coupon);
        opLog.log("coupon", "创建优惠券「" + (saved.getName() == null ? "" : saved.getName()) + "」");
        return R.ok("发布成功", saved);
    }

    /** F-A-052 编辑优惠券 */
    @PutMapping("/coupon/{id}")
    @PreAuthorize("hasAuthority('coupon:manage')")
    public R<TCoupon> update(@PathVariable Long id, @RequestBody TCoupon patch) {
        TCoupon saved = couponService.update(id, patch);
        opLog.log("coupon", "更新优惠券 id=" + id);
        return R.ok("更新成功", saved);
    }

    /** F-A-053 物理删除优惠券（同时清除用户已领取记录） */
    @DeleteMapping("/coupon/{id}")
    @PreAuthorize("hasAuthority('coupon:manage')")
    public R<Void> delete(@PathVariable Long id) {
        couponService.delete(id);
        opLog.log("coupon", "删除优惠券 id=" + id);
        return R.<Void>okMsg("已删除");
    }

    /** 满减活动列表（演示空实现） */
    @GetMapping("/promotion/list")
    @PreAuthorize("hasAuthority('promotion:manage')")
    public R<List<TPromotion>> promotions() {
        return R.ok(List.of());
    }
}