package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 成长值流水表 (PRD §7.5)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_growth_log")
public class TGrowthLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 变动成长值 */
    private Integer changeAmount;

    /** 变动后成长值 */
    private Integer balanceAfter;

    /** 类型：ORDER 下单获得 / REFUND 退款扣减 */
    private String type;

    /** 备注 */
    private String reason;

    /** 关联订单 ID */
    private Long relatedOrderId;

    /** t_growth_log 没有 updated_at，BaseEntity 字段不映射 */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}