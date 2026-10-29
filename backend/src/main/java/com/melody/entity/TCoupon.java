package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券定义表 (PRD §7.4)
 *
 * type 字段可改成 CouponTypeEnum + @EnumValue；折扣券 discount 字段存折扣率（0.8），
 * 满减券存减金额（10.00 / 30.00），具体语义在 service 层判断。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_coupon")
public class TCoupon extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 优惠券名称 */
    private String name;

    /** 类型：满减/折扣 */
    private String type;

    /** 面额：满减券为减金额，折扣券为折扣率（如 0.8） */
    private BigDecimal discount;

    /** 使用门槛，满 X 元可用 */
    private BigDecimal minSpend;

    /** 发行总量 */
    private Integer total;

    /** 剩余库存 */
    private Integer remaining;

    /** 活动开始时间 */
    private LocalDateTime startDate;

    /** 活动结束时间 */
    private LocalDateTime endDate;

    /** 状态 1启用 0禁用 */
    private Integer status;

    /** 说明 */
    private String description;
}