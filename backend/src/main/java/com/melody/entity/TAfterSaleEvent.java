package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 售后工单 Agent 事件（意图/风控/政策/决策/退款/回复），用于审计与排障。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_after_sale_event")
public class TAfterSaleEvent extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    /** INTENT / ORDER / RISK / POLICY / DECISION / REFUND / REPLY */
    private String step;

    /** agent / system / admin */
    private String actor;

    /** 节点输入输出 JSON，便于回放 */
    private String payload;
}
