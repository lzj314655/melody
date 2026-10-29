package com.melody.entity.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderStatusEnum.of() 测试。
 */
@DisplayName("OrderStatusEnum 测试")
class OrderStatusEnumTest {

    @Test
    @DisplayName("of 合法 code 返回对应枚举")
    void ofValid() {
        assertThat(OrderStatusEnum.of("PENDING_PAY")).isEqualTo(OrderStatusEnum.PENDING_PAY);
        assertThat(OrderStatusEnum.of("PENDING_SHIP")).isEqualTo(OrderStatusEnum.PENDING_SHIP);
        assertThat(OrderStatusEnum.of("PENDING_RECEIVE")).isEqualTo(OrderStatusEnum.PENDING_RECEIVE);
        assertThat(OrderStatusEnum.of("COMPLETED")).isEqualTo(OrderStatusEnum.COMPLETED);
        assertThat(OrderStatusEnum.of("AFTER_SALE")).isEqualTo(OrderStatusEnum.AFTER_SALE);
        assertThat(OrderStatusEnum.of("REFUNDED")).isEqualTo(OrderStatusEnum.REFUNDED);
        assertThat(OrderStatusEnum.of("CANCELED")).isEqualTo(OrderStatusEnum.CANCELED);
    }

    @Test
    @DisplayName("of 非法 code 返回 null")
    void ofInvalid() {
        assertThat(OrderStatusEnum.of("UNKNOWN")).isNull();
        assertThat(OrderStatusEnum.of("")).isNull();
        assertThat(OrderStatusEnum.of(null)).isNull();
    }

    @Test
    @DisplayName("枚举 @EnumValue code 唯一")
    void codesUnique() {
        long count = java.util.Arrays.stream(OrderStatusEnum.values())
                .map(OrderStatusEnum::getCode)
                .distinct()
                .count();
        assertThat(count).isEqualTo(OrderStatusEnum.values().length);
    }

    @Test
    @DisplayName("label 都不是空字符串")
    void labelsNotEmpty() {
        for (OrderStatusEnum e : OrderStatusEnum.values()) {
            assertThat(e.getLabel()).isNotBlank();
        }
    }
}
