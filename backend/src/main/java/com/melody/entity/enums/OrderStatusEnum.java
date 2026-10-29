package com.melody.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 订单状态枚举 — PRD §7.1
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    PENDING_PAY("PENDING_PAY", "待付款"),
    PENDING_SHIP("PENDING_SHIP", "待发货"),
    PENDING_RECEIVE("PENDING_RECEIVE", "待收货"),
    COMPLETED("COMPLETED", "已完成"),
    AFTER_SALE("AFTER_SALE", "售后中"),
    REFUNDED("REFUNDED", "已退款"),
    CANCELED("CANCELED", "已取消");

    @EnumValue
    private final String code;
    private final String label;

    public static OrderStatusEnum of(String code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}