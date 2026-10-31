package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.ResultCode;
import com.melody.dto.AfterSaleDTOs;
import com.melody.entity.TAfterSaleEvent;
import com.melody.entity.TAfterSaleTicket;
import com.melody.entity.TOrder;
import com.melody.entity.TUser;
import com.melody.exception.BizException;
import com.melody.mapper.TAfterSaleEventMapper;
import com.melody.mapper.TAfterSaleTicketMapper;
import com.melody.mapper.TOrderMapper;
import com.melody.mapper.TUserMapper;
import com.melody.service.IAfterSaleService;
import com.melody.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleServiceImpl implements IAfterSaleService {

    private static final DateTimeFormatter TICKET_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TAfterSaleTicketMapper ticketMapper;
    private final TAfterSaleEventMapper eventMapper;
    private final TOrderMapper orderMapper;
    private final TUserMapper userMapper;
    private final IOrderService orderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAfterSaleTicket create(AfterSaleDTOs.CreateTicketRequest req) {
        if (req == null || req.getUserId() == null || req.getOrderId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "售后工单缺少用户或订单");
        }
        // 订单归属校验，防止 Python 侧被伪造参数
        TOrder order = orderService.getByIdForUser(req.getOrderId(), req.getUserId());

        TAfterSaleTicket t = new TAfterSaleTicket();
        t.setTicketNo(req.getTicketNo() == null || req.getTicketNo().isBlank()
                ? genTicketNo() : req.getTicketNo());
        t.setUserId(req.getUserId());
        t.setOrderId(req.getOrderId());
        t.setIntent(req.getIntent());
        t.setStatus(req.getStatus() == null || req.getStatus().isBlank()
                ? "PENDING_REVIEW" : req.getStatus());
        t.setRiskLevel(req.getRiskLevel());
        t.setRiskScore(req.getRiskScore() == null ? 0 : req.getRiskScore());
        t.setDecision(req.getDecision());
        t.setDecisionReason(req.getDecisionReason());
        t.setRefundAmount(req.getRefundAmount() == null ? order.getTotalPrice() : req.getRefundAmount());
        t.setThreadId(req.getThreadId());
        t.setReply(req.getReply());
        ticketMapper.insert(t);
        addEvent(t.getId(), "CREATE", "agent",
                "orderId=" + t.getOrderId() + ", intent=" + t.getIntent()
                        + ", status=" + t.getStatus() + ", decision=" + t.getDecision());
        return t;
    }

    @Override
    public TAfterSaleTicket get(Long id) {
        TAfterSaleTicket t = ticketMapper.selectById(id);
        if (t == null) throw new BizException(ResultCode.NOT_FOUND, "售后工单不存在");
        return t;
    }

    @Override
    public List<TAfterSaleTicket> list(String status) {
        LambdaQueryWrapper<TAfterSaleTicket> qw = new LambdaQueryWrapper<TAfterSaleTicket>()
                .orderByDesc(TAfterSaleTicket::getId);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            qw.eq(TAfterSaleTicket::getStatus, status);
        }
        return ticketMapper.selectList(qw);
    }

    @Override
    public List<TAfterSaleTicket> listByUser(Long userId) {
        return ticketMapper.selectList(new LambdaQueryWrapper<TAfterSaleTicket>()
                .eq(TAfterSaleTicket::getUserId, userId)
                .orderByDesc(TAfterSaleTicket::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAfterSaleTicket update(Long id, AfterSaleDTOs.UpdateTicketRequest req) {
        TAfterSaleTicket t = get(id);
        if (req.getStatus() != null && !req.getStatus().equals(t.getStatus())) {
            int affected = ticketMapper.markStatus(id, t.getStatus(), req.getStatus(),
                    req.getDecision(), req.getDecisionReason());
            if (affected != 1) {
                throw new BizException(ResultCode.BAD_REQUEST, "工单状态已变化，请刷新后重试");
            }
        }
        if (req.getReply() != null) ticketMapper.updateReply(id, req.getReply());
        if (req.getAssignee() != null) ticketMapper.updateAssignee(id, req.getAssignee());
        addEvent(id, "UPDATE", "agent",
                "status=" + req.getStatus() + ", decision=" + req.getDecision());
        return get(id);
    }

    @Override
    public void addEvent(Long ticketId, String step, String actor, String payload) {
        TAfterSaleEvent e = new TAfterSaleEvent();
        e.setTicketId(ticketId);
        e.setStep(step == null ? "UNKNOWN" : step);
        e.setActor(actor == null ? "agent" : actor);
        e.setPayload(payload);
        eventMapper.insert(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAfterSaleTicket refund(Long ticketId) {
        TAfterSaleTicket t = get(ticketId);
        if ("REFUNDED".equals(t.getStatus())) return t;
        if (!"APPROVED".equals(t.getStatus())) {
            int affected = ticketMapper.markStatus(ticketId, "PENDING_REVIEW", "APPROVED",
                    "AUTO_APPROVE", t.getDecisionReason());
            if (affected != 1) {
                throw new BizException(ResultCode.BAD_REQUEST, "工单状态不允许自动退款");
            }
            t.setStatus("APPROVED");
        }
        doRefund(t);
        ticketMapper.markStatus(ticketId, "APPROVED", "REFUNDED", "AUTO_APPROVE", t.getDecisionReason());
        addEvent(ticketId, "REFUND", "system", "auto refund success, amount=" + t.getRefundAmount());
        return get(ticketId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAfterSaleTicket decide(Long ticketId, String action, String reason,
                                   BigDecimal amount, String admin) {
        TAfterSaleTicket t = get(ticketId);
        if (!"PENDING_REVIEW".equals(t.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅待审核工单可处理");
        }
        if ("REJECT".equalsIgnoreCase(action)) {
            ticketMapper.markStatus(ticketId, "PENDING_REVIEW", "REJECTED", "REJECT",
                    reason == null ? t.getDecisionReason() : reason);
            ticketMapper.updateAssignee(ticketId, admin);
            addEvent(ticketId, "DECISION", "admin", "reject: " + reason);
            return get(ticketId);
        }
        if (!"APPROVE".equalsIgnoreCase(action)) {
            throw new BizException(ResultCode.BAD_REQUEST, "action 仅支持 APPROVE / REJECT");
        }
        ticketMapper.markStatus(ticketId, "PENDING_REVIEW", "APPROVED", "MANUAL_APPROVE",
                reason == null ? t.getDecisionReason() : reason);
        ticketMapper.updateAssignee(ticketId, admin);
        if (amount != null) {
            t.setRefundAmount(amount);
        }
        doRefund(t);
        ticketMapper.markStatus(ticketId, "APPROVED", "REFUNDED", "MANUAL_APPROVE",
                reason == null ? t.getDecisionReason() : reason);
        addEvent(ticketId, "DECISION", "admin", "approve & refund by " + admin);
        return get(ticketId);
    }

    /**
     * 执行退款：订单从 PENDING_RECEIVE / COMPLETED 申请售后 → AFTER_SALE → REFUNDED。
     * 订单已退款则直接返回，保证工单重试幂等。
     */
    private void doRefund(TAfterSaleTicket t) {
        TOrder order = orderService.getByIdForUser(t.getOrderId(), t.getUserId());
        if ("REFUNDED".equals(order.getStatus())) {
            return;
        }
        if ("PENDING_RECEIVE".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) {
            orderService.refund(t.getUserId(), t.getOrderId(),
                    t.getDecisionReason() == null ? "售后工单退款" : t.getDecisionReason());
        } else if (!"AFTER_SALE".equals(order.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "订单当前状态（" + order.getStatus() + "）不可退款");
        }
        orderService.approveRefund(t.getOrderId());
    }

    @Override
    public Map<String, Object> riskFeatures(Long userId, Long productId) {
        TUser user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ResultCode.USER_NOT_FOUND);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime d30 = now.minusDays(30);
        LocalDateTime d90 = now.minusDays(90);

        long orders30 = orderMapper.countOrdersSince(userId, d30);
        long orders90 = orderMapper.countOrdersSince(userId, d90);
        long refunds30 = orderMapper.countRefundOrdersSince(userId, d30);
        long refunds90 = orderMapper.countRefundOrdersSince(userId, d90);
        BigDecimal refundAmount90 = orderMapper.sumRefundAmountSince(userId, d90);
        long productRefunds = productId == null ? 0
                : orderMapper.countProductRefundSince(userId, productId, d90);

        boolean blacklist = user.getStatus() != null && user.getStatus() == 0;
        long accountAgeDays = user.getJoinDate() == null ? 999
                : Math.max(0, java.time.Duration.between(user.getJoinDate(), now).toDays());
        double refundRate90 = orders90 == 0 ? 0
                : Math.min(1.0, (double) refunds90 / orders90);

        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (blacklist) { score += 60; reasons.add("账号已被冻结/黑名单"); }
        if (refundRate90 > 0.6) { score += 25; reasons.add("90 天退款率 " + pct(refundRate90)); }
        else if (refundRate90 > 0.3) { score += 12; reasons.add("90 天退款率 " + pct(refundRate90)); }
        if (refunds30 >= 3) { score += 20; reasons.add("30 天退款 " + refunds30 + " 次"); }
        if (accountAgeDays < 7) { score += 15; reasons.add("账号注册仅 " + accountAgeDays + " 天"); }
        if (productRefunds >= 3) { score += 20; reasons.add("同商品 90 天退款 " + productRefunds + " 次"); }
        if (refundAmount90 != null && refundAmount90.compareTo(new BigDecimal("1000")) > 0) {
            score += 10; reasons.add("90 天退款金额 ￥" + refundAmount90.setScale(2, RoundingMode.HALF_UP));
        }
        score = Math.min(100, score);
        String level = score >= 70 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW";

        Map<String, Object> out = new HashMap<>();
        out.put("userId", userId);
        out.put("score", score);
        out.put("level", level);
        out.put("reasons", reasons);
        out.put("refundRate90d", Math.round(refundRate90 * 10000.0) / 10000.0);
        out.put("refundCount30d", refunds30);
        out.put("refundCount90d", refunds90);
        out.put("orderCount30d", orders30);
        out.put("refundAmount90d", refundAmount90 == null ? BigDecimal.ZERO : refundAmount90);
        out.put("productRefundCount90d", productRefunds);
        out.put("accountAgeDays", accountAgeDays);
        out.put("blacklist", blacklist);
        return out;
    }

    private static String pct(double v) {
        return Math.round(v * 1000.0) / 10.0 + "%";
    }

    private static String genTicketNo() {
        return "AS-" + LocalDateTime.now().format(TICKET_FMT) + "-"
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
