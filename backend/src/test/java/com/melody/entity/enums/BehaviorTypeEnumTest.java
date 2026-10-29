package com.melody.entity.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BehaviorTypeEnum 测试 — 权重映射需与 ItemCFEngine.WEIGHT_MAP 一致。
 */
@DisplayName("BehaviorTypeEnum 测试")
class BehaviorTypeEnumTest {

    @Test
    @DisplayName("权重映射符合 PRD §4.3.3 定义")
    void weightMapping() {
        assertThat(BehaviorTypeEnum.VIEW.getWeight()).isEqualTo(1);
        assertThat(BehaviorTypeEnum.REVIEW.getWeight()).isEqualTo(2);
        assertThat(BehaviorTypeEnum.ADD_CART.getWeight()).isEqualTo(3);
        assertThat(BehaviorTypeEnum.FAVORITE.getWeight()).isEqualTo(4);
        assertThat(BehaviorTypeEnum.ORDER.getWeight()).isEqualTo(5);
    }

    @Test
    @DisplayName("@EnumValue code 是字符串")
    void codeValues() {
        assertThat(BehaviorTypeEnum.VIEW.getCode()).isEqualTo("VIEW");
        assertThat(BehaviorTypeEnum.ADD_CART.getCode()).isEqualTo("ADD_CART");
        assertThat(BehaviorTypeEnum.FAVORITE.getCode()).isEqualTo("FAVORITE");
        assertThat(BehaviorTypeEnum.REVIEW.getCode()).isEqualTo("REVIEW");
        assertThat(BehaviorTypeEnum.ORDER.getCode()).isEqualTo("ORDER");
    }

    @Test
    @DisplayName("5 个枚举覆盖全部真实行为")
    void enumCount() {
        assertThat(BehaviorTypeEnum.values()).hasSize(5);
    }
}
