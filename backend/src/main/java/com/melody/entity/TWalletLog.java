package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水表
 *
 * t_wallet_log 没有 updated_at 字段，用 @TableField(exist = false) 屏蔽 BaseEntity 的 updatedAt。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_wallet_log")
public class TWalletLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 变动金额（正为入账，负为出账） */
    private BigDecimal changeAmount;

    /** 变动后余额 */
    private BigDecimal balanceAfter;

    /** 类型：RECHARGE 充值 / PAY 支付 / REFUND 退款 / ADMIN_ADJUST 管理员调整 */
    private String type;

    /** 备注 */
    private String reason;

    /** 关联订单 ID（支付/退款时） */
    private Long relatedOrderId;

    /** 操作人 */
    private String operator;

    /** t_wallet_log 没有 updated_at，BaseEntity 字段不映射 */
    @TableField(exist = false)
    private LocalDateTime updatedAt;

    /** t_wallet_log 没有 created_at，覆盖为日志时间 */
    @TableField(value = "created_at")
    private LocalDateTime logTime;
}