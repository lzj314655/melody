package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 积分流水表 (PRD §7.3)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_point_log")
public class TPointLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 变动积分（正为入账，负为出账） */
    private Integer changeAmount;

    /** 变动后积分余额 */
    private Integer balanceAfter;

    /** 类型：ORDER 下单赠送 / REVIEW 评价 / REGISTER 注册礼包 / CANCEL 取消回退 */
    private String type;

    /** 备注 */
    private String reason;

    /** 关联订单 ID */
    private Long relatedOrderId;

    /** t_point_log 没有 updated_at，BaseEntity 字段不映射 */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}