package com.melody.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResultCode 枚举值校验：避免有人误改枚举值导致前端/文档对不上。
 */
@DisplayName("ResultCode 枚举值测试")
class ResultCodeTest {

    @Test
    @DisplayName("SUCCESS = 200")
    void success() {
        assertThat(ResultCode.SUCCESS.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("FAIL = 500")
    void fail() {
        assertThat(ResultCode.FAIL.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("通用错误码 1xxx 段")
    void genericCodes() {
        assertThat(ResultCode.BAD_REQUEST.getCode()).isEqualTo(1001);
        assertThat(ResultCode.UNAUTHORIZED.getCode()).isEqualTo(1002);
        assertThat(ResultCode.FORBIDDEN.getCode()).isEqualTo(1003);
        assertThat(ResultCode.NOT_FOUND.getCode()).isEqualTo(1004);
    }

    @Test
    @DisplayName("用户模块 2xxx 段")
    void userCodes() {
        assertThat(ResultCode.USER_NOT_FOUND.getCode()).isEqualTo(2001);
        assertThat(ResultCode.USER_PASSWORD_ERROR.getCode()).isEqualTo(2002);
        assertThat(ResultCode.USER_ALREADY_EXISTS.getCode()).isEqualTo(2003);
        assertThat(ResultCode.USER_BLACKLISTED.getCode()).isEqualTo(2004);
    }

    @Test
    @DisplayName("商品模块 3xxx 段")
    void productCodes() {
        assertThat(ResultCode.PRODUCT_NOT_FOUND.getCode()).isEqualTo(3001);
        assertThat(ResultCode.PRODUCT_OFF_SHELF.getCode()).isEqualTo(3002);
        assertThat(ResultCode.PRODUCT_OUT_OF_STOCK.getCode()).isEqualTo(3003);
        assertThat(ResultCode.SKU_NOT_FOUND.getCode()).isEqualTo(3004);
    }

    @Test
    @DisplayName("订单模块 4xxx 段")
    void orderCodes() {
        assertThat(ResultCode.ORDER_NOT_FOUND.getCode()).isEqualTo(4001);
        assertThat(ResultCode.ORDER_STATUS_INVALID.getCode()).isEqualTo(4002);
        assertThat(ResultCode.ORDER_REFUND_FAILED.getCode()).isEqualTo(4003);
        assertThat(ResultCode.CART_EMPTY.getCode()).isEqualTo(4004);
    }

    @Test
    @DisplayName("钱包 / 支付 5xxx 段")
    void walletCodes() {
        assertThat(ResultCode.WALLET_BALANCE_INSUFFICIENT.getCode()).isEqualTo(5001);
        assertThat(ResultCode.PAYMENT_FAILED.getCode()).isEqualTo(5002);
    }

    @Test
    @DisplayName("推荐 / 评价 6xxx 段")
    void recReviewCodes() {
        assertThat(ResultCode.RECOMMEND_FAILED.getCode()).isEqualTo(6001);
        assertThat(ResultCode.REVIEW_NOT_PURCHASED.getCode()).isEqualTo(6010);
        assertThat(ResultCode.REVIEW_ALREADY_EXISTS.getCode()).isEqualTo(6011);
        assertThat(ResultCode.REVIEW_NOT_FOUND.getCode()).isEqualTo(6012);
    }

    @Test
    @DisplayName("INTERNAL_ERROR = 9999")
    void internal() {
        assertThat(ResultCode.INTERNAL_ERROR.getCode()).isEqualTo(9999);
    }

    @Test
    @DisplayName("所有枚举 code 唯一")
    void codesUnique() {
        long count = java.util.Arrays.stream(ResultCode.values())
                .map(ResultCode::getCode)
                .distinct()
                .count();
        assertThat(count).isEqualTo(ResultCode.values().length);
    }
}
