package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 售后多 Agent 工单。
 *
 * <p>status：PENDING_REVIEW / APPROVED / REJECTED / REFUNDED / CLOSED</p>
 * <p>decision：AUTO_APPROVE / MANUAL_REVIEW / REJECT</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_after_sale_ticket")
public class TAfterSaleTicket extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;

    private Long userId;

    private Long orderId;

    /** LOGISTICS / QUALITY / REFUND / MALICIOUS / OTHER */
    private String intent;

    private String status;

    /** LOW / MEDIUM / HIGH */
    private String riskLevel;

    private Integer riskScore;

    private String decision;

    private String decisionReason;

    private BigDecimal refundAmount;

    /** LangGraph 线程 ID，保留人工恢复入口 */
    private String threadId;

    private String reply;

    private String assignee;
}
