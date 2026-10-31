package com.melody.service;

import com.melody.dto.AfterSaleDTOs;
import com.melody.entity.TAfterSaleTicket;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 售后多 Agent 工单服务。
 */
public interface IAfterSaleService {

    TAfterSaleTicket create(AfterSaleDTOs.CreateTicketRequest req);

    TAfterSaleTicket get(Long id);

    List<TAfterSaleTicket> list(String status);

    List<TAfterSaleTicket> listByUser(Long userId);

    TAfterSaleTicket update(Long id, AfterSaleDTOs.UpdateTicketRequest req);

    void addEvent(Long ticketId, String step, String actor, String payload);

    /** 自动通过后执行退款（幂等：工单已 REFUNDED 直接返回） */
    TAfterSaleTicket refund(Long ticketId);

    /** 管理员审核：APPROVE / REJECT */
    TAfterSaleTicket decide(Long ticketId, String action, String reason,
                            BigDecimal amount, String admin);

    /** 风控特征：退款率、退款次数、账号年龄、黑名单、同商品重复退款等 */
    Map<String, Object> riskFeatures(Long userId, Long productId);
}
