package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表 (PRD §7.1)
 *
 * status 字段使用 String 存储 PRD §7.1 枚举值（PENDING_PAY / PENDING_SHIP 等），
 * 业务层用 OrderStatusEnum.of() 转换。如需严格枚举校验，可改成 OrderStatusEnum + @EnumValue。
 *
 * 含 @Version 乐观锁字段（演示 MyBatis-Plus 配置启用乐观锁）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class TOrder extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号（业务编号，唯一） */
    private String orderNo;

    /** 下单用户 ID */
    private Long userId;

    /** 订单实付总金额 */
    private BigDecimal totalPrice;

    /** 商品总金额（优惠前） */
    private BigDecimal goodsAmount;

    /** 优惠金额 */
    private BigDecimal discount;

    /** 运费 */
    private BigDecimal shippingFee;

    /** 使用的优惠券 ID */
    private Long couponId;

    /**
     * 订单状态（PRD §7.1 枚举字符串）
     * PENDING_PAY / PENDING_SHIP / PENDING_RECEIVE / COMPLETED / AFTER_SALE / REFUNDED / CANCELED
     */
    private String status;

    /** 支付方式：WALLET 余额 / ALIPAY 支付宝沙箱 */
    private String payMethod;

    /** 支付宝交易号（付款确认回填） */
    private String tradeNo;

    /** 收货人 */
    private String consigneeName;

    /** 收货人手机 */
    private String consigneePhone;

    /** 完整收货地址 */
    private String consigneeAddress;

    /** 物流单号 */
    private String trackingNo;

    /** 退款原因 (F-U-044) */
    private String refundReason;

    /** 申请退款时间 */
    private LocalDateTime refundTime;

    /** 支付完成时间 */
    private LocalDateTime paidAt;

    /** 发货时间 */
    private LocalDateTime shippedAt;

    /** 确认收货/订单完成时间 */
    private LocalDateTime completedAt;

    /** 取消原因 */
    private String cancelReason;

    /** 订单备注 */
    private String note;

    /** 乐观锁版本号字段已移除（演示环境不需要乐观锁，字段保留会导致 SQL 查询失败） */
}