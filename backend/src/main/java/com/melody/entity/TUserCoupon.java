package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户持有优惠券表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_coupon")
public class TUserCoupon extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 优惠券 ID */
    private Long couponId;

    /** 状态：可用/已用/过期 */
    private String status;

    /** 使用时间 */
    private LocalDateTime usedAt;

    /** 使用该券的订单 ID */
    private Long usedOrderId;

    /** 过期时间 */
    private LocalDateTime expireAt;

    /** 领取时间（覆盖 BaseEntity 的 createdAt 语义） */
    @TableField(value = "claimed_at")
    private LocalDateTime claimedAt;
}