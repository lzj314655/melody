package com.melody.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 优惠券类型 — PRD §7.4
 */
@Getter
@AllArgsConstructor
public enum CouponTypeEnum {

    FULL_CUT("满减", "满减券"),
    DISCOUNT("折扣", "折扣券");

    @EnumValue
    private final String code;
    private final String label;
}