package com.melody.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户行为类型 — PRD §4.3.3
 */
@Getter
@AllArgsConstructor
public enum BehaviorTypeEnum {

    VIEW("VIEW", 1),
    ADD_CART("ADD_CART", 3),
    FAVORITE("FAVORITE", 4),
    REVIEW("REVIEW", 2),
    ORDER("ORDER", 5);

    @EnumValue
    private final String code;
    /** 隐式反馈权重 */
    private final Integer weight;
}