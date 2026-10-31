package com.melody.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 售后工单相关请求/响应。
 */
public class AfterSaleDTOs {

    /** Python LangGraph 创建工单 */
    @Data
    public static class CreateTicketRequest {
        private String ticketNo;
        private Long userId;
        private Long orderId;
        private String intent;
        private String status;
        private String riskLevel;
        private Integer riskScore;
        private String decision;
        private String decisionReason;
        private BigDecimal refundAmount;
        private String threadId;
        private String reply;
    }

    /** Python 更新工单状态/回复 */
    @Data
    public static class UpdateTicketRequest {
        private String status;
        private String decision;
        private String decisionReason;
        private String reply;
        private String assignee;
    }

    /** Python 追加 Agent 事件 */
    @Data
    public static class EventRequest {
        private String step;
        private String actor;
        private String payload;
    }

    /** 管理员审核 */
    @Data
    public static class DecisionRequest {
        /** APPROVE / REJECT */
        private String action;
        private String reason;
        private BigDecimal amount;
    }
}
