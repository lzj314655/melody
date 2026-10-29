package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 钱包充值订单（支付宝沙箱 PC 网页支付）
 *
 * 幂等：付款确认时仅把 PENDING→PAID 命中一次的记录加余额，
 * 重复查询 / 重复异步通知都不会重复入账。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_wallet_recharge_order")
public class TWalletRechargeOrder extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户订单号（本系统生成，唯一） */
    private String outTradeNo;

    /** 充值用户 ID */
    private Long userId;

    /** 充值金额（元） */
    private BigDecimal amount;

    /** PENDING 待付 / PAID 已付 / CLOSED 已关闭 */
    private String status;

    /** 支付宝交易号（付款后回填） */
    private String tradeNo;
}
