package com.melody.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 下单 / 退款 / 购物车相关 DTO
 */
public class CheckoutDTOs {

    @Data
    public static class CartAddRequest {
        @NotNull
        private Long productId;
        @NotNull
        private Long skuId;
        @NotNull
        private Integer quantity;
        // 注意：价格与规格名一律由服务端按 skuId 查询，绝不接受客户端传入，
        // 否则可构造 price=0.01 的请求以极低价格下单。
    }

    @Data
    public static class CheckoutRequest {
        /** 选中的购物车项 ID 列表（F-U-034） */
        @NotEmpty
        private List<Long> cartItemIds;
        /** 收货地址 ID */
        @NotNull
        private Long addressId;
        /** 用户优惠券 ID（可空） */
        private Long userCouponId;
        /** 支付方式：WALLET 余额（默认）/ ALIPAY 支付宝沙箱 */
        private String payMethod;
    }

    @Data
    public static class RefundRequest {
        /** PRD §7.4: 商品质量问题/不想要了/发错货了/其他 */
        @NotEmpty
        private String reason;
    }

    /**
     * 购物车项视图（带商品名称/封面图，供前端列表展示）。
     * 字段名与前端 cart.js 中 store 的预期一致，避免再多一层 product 嵌套。
     */
    @Data
    public static class CartItemVO {
        private Long id;
        private Long productId;
        private Long skuId;
        private Integer quantity;
        private java.math.BigDecimal price;
        private String specName;
        private Integer selected;

        /** 冗余：商品名（来自 t_product.name） */
        private String productName;
        /** 冗余：商品封面图（来自 t_product.cover_image） */
        private String productImage;
    }
}