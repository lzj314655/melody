package com.melody.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包充值（支付宝沙箱）相关 VO
 */
public class RechargeDTOs {

    /** 下单返回：商户订单号 + 支付宝支付页表单（自提交 HTML） */
    @Data
    public static class RechargeOrderVO {
        private String outTradeNo;
        private String payForm;
    }

    /** 主动查询返回：订单状态 + 入账后余额（仅 PAID 返回） */
    @Data
    public static class RechargeQueryVO {
        private String outTradeNo;
        /** PENDING 待付 / PAID 已付入账 / CLOSED 已关闭 / FAILED 查询异常 */
        private String status;
        private BigDecimal balance;
    }

    // ============ 订单支付宝支付 ============

    /** 订单支付下单返回：订单号 + 支付宝支付页表单 */
    @Data
    public static class OrderPayFormVO {
        private String orderNo;
        private String payForm;
    }

    /** 订单支付查询返回：状态 + 支付时间（PAID 时返回） */
    @Data
    public static class OrderPayQueryVO {
        private String orderNo;
        /** PENDING 待付 / PAID 已付 */
        private String status;
        private String paidAt;
    }
}
