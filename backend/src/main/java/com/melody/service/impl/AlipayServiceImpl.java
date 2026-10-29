package com.melody.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.ResultCode;
import com.melody.config.AlipayConfig;
import com.melody.dto.RechargeDTOs;
import com.melody.entity.TOrder;
import com.melody.entity.TWalletRechargeOrder;
import com.melody.exception.BizException;
import com.melody.mapper.TWalletRechargeOrderMapper;
import com.melody.service.IAlipayService;
import com.melody.service.IOrderService;
import com.melody.service.IWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付宝沙箱充值实现。
 *
 * 幂等核心：confirmPaid 在单事务内执行
 *   UPDATE status='PAID' WHERE status='PENDING'   （affected=1 才加余额）
 * 重复的查询 / 重复的异步通知都只会命中 0 行，不会重复入账。
 *
 * 注：alipay.trade.query / 通知验签不在事务内（涉及网络/外部），仅标单+加余额这一段加事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayServiceImpl implements IAlipayService {

    private final AlipayClient alipayClient;
    private final AlipayConfig alipayConfig;
    private final TWalletRechargeOrderMapper orderMapper;
    private final IWalletService walletService;
    private final IOrderService orderService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate alipayTransactionTemplate;

    private static final BigDecimal MAX_RECHARGE = new BigDecimal("100000");

    @Override
    public RechargeDTOs.RechargeOrderVO createRechargeOrder(Long userId, BigDecimal amount) {
        if (userId == null) throw new BizException(ResultCode.UNAUTHORIZED);
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "充值金额必须大于 0");
        }
        if (amount.compareTo(MAX_RECHARGE) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "单次充值金额不得超过 ￥100,000");
        }

        String outTradeNo = newOutTradeNo(userId);
        TWalletRechargeOrder order = new TWalletRechargeOrder();
        order.setOutTradeNo(outTradeNo);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus("PENDING");
        orderMapper.insert(order);

        RechargeDTOs.RechargeOrderVO vo = new RechargeDTOs.RechargeOrderVO();
        vo.setOutTradeNo(outTradeNo);
        vo.setPayForm(buildPagePayForm(outTradeNo, amount));
        return vo;
    }

    private String buildPagePayForm(String outTradeNo, BigDecimal amount) {
        AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
        req.setNotifyUrl(alipayConfig.getNotifyUrl());
        req.setReturnUrl(alipayConfig.getReturnUrl());

        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", outTradeNo);
        biz.put("total_amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        biz.put("subject", "麦乐迪钱包充值");
        biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
        try {
            req.setBizContent(objectMapper.writeValueAsString(biz));
        } catch (Exception e) {
            throw new BizException(ResultCode.PAYMENT_FAILED, "构建支付请求失败");
        }

        try {
            AlipayTradePagePayResponse resp = alipayClient.pageExecute(req);
            return resp.getBody();
        } catch (Exception e) {
            log.error("alipay trade.page.pay failed [{}]: {}", outTradeNo, e.getMessage(), e);
            throw new BizException(ResultCode.PAYMENT_FAILED, "调用支付宝下单失败：" + e.getMessage());
        }
    }

    @Override
    public RechargeDTOs.RechargeQueryVO queryOrder(String outTradeNo, Long userId) {
        TWalletRechargeOrder order = loadOwnedOrder(outTradeNo, userId);
        RechargeDTOs.RechargeQueryVO vo = new RechargeDTOs.RechargeQueryVO();
        vo.setOutTradeNo(outTradeNo);

        // 已入账：直接返回当前余额
        if ("PAID".equals(order.getStatus())) {
            vo.setStatus("PAID");
            vo.setBalance(walletService.getAccount(userId).getBalance());
            return vo;
        }
        if ("CLOSED".equals(order.getStatus())) {
            vo.setStatus("CLOSED");
            return vo;
        }

        // PENDING：主动查支付宝
        AlipayTradeQueryResponse resp = doQuery(outTradeNo, order.getTradeNo());
        if (resp != null && resp.isSuccess()
                && ("TRADE_SUCCESS".equals(resp.getTradeStatus())
                    || "TRADE_FINISHED".equals(resp.getTradeStatus()))) {
            BigDecimal bal = confirmPaid(outTradeNo, resp.getTradeNo());
            vo.setStatus("PAID");
            vo.setBalance(bal);
        } else {
            // 未付 / 查询失败 / 等待买家付款 —— 继续轮询
            vo.setStatus("PENDING");
        }
        return vo;
    }

    @Override
    public String handleNotify(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "fail";
        try {
            boolean signOk = AlipaySignature.rsaCheckV1(params,
                    alipayConfig.getPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType());
            if (!signOk) {
                log.warn("alipay notify 验签失败: out_trade_no={}", params.get("out_trade_no"));
                return "fail";
            }
            String tradeStatus = params.get("trade_status");
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                confirmPaid(outTradeNo, tradeNo);
            }
            return "success";
        } catch (Exception e) {
            log.error("alipay notify 处理异常: {}", e.getMessage(), e);
            return "fail";
        }
    }

    /**
     * 付款确认（幂等）：单事务内 PENDING→PAID 命中才加余额。
     * 被主动查询和异步通知共用。
     */
    private BigDecimal confirmPaid(String outTradeNo, String tradeNo) {
        return alipayTransactionTemplate.execute(status -> {
            int affected = orderMapper.markPaid(outTradeNo, tradeNo == null ? "" : tradeNo);
            TWalletRechargeOrder o = orderMapper.selectOne(new LambdaQueryWrapper<TWalletRechargeOrder>()
                    .eq(TWalletRechargeOrder::getOutTradeNo, outTradeNo));
            if (o == null) return BigDecimal.ZERO;
            if (affected == 1) {
                walletService.rechargeByGateway(o.getUserId(), o.getAmount(), outTradeNo);
            }
            return walletService.getAccount(o.getUserId()).getBalance();
        });
    }

    private AlipayTradeQueryResponse doQuery(String outTradeNo, String tradeNo) {
        AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", outTradeNo);
        if (tradeNo != null) biz.put("trade_no", tradeNo);
        try {
            req.setBizContent(objectMapper.writeValueAsString(biz));
            return alipayClient.execute(req);
        } catch (Exception e) {
            log.error("alipay trade.query failed [{}]: {}", outTradeNo, e.getMessage(), e);
            return null;
        }
    }

    private TWalletRechargeOrder loadOwnedOrder(String outTradeNo, Long userId) {
        if (outTradeNo == null || outTradeNo.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "缺少订单号");
        }
        TWalletRechargeOrder o = orderMapper.selectOne(new LambdaQueryWrapper<TWalletRechargeOrder>()
                .eq(TWalletRechargeOrder::getOutTradeNo, outTradeNo));
        if (o == null) throw new BizException(ResultCode.NOT_FOUND, "充值订单不存在");
        if (!o.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作该订单");
        }
        return o;
    }

    /** 商户订单号：rc + userId + 13 位时间 + 4 位随机，唯一可读 */
    private String newOutTradeNo(Long userId) {
        long t = System.currentTimeMillis();
        int r = ThreadLocalRandom.current().nextInt(10000);
        return String.format("rc%d%d%04d", userId, t, r);
    }

    // ===================== 订单支付宝支付 =====================

    @Override
    public RechargeDTOs.OrderPayFormVO createOrderPayForm(Long userId, Long orderId) {
        TOrder order = loadUserOrder(orderId, userId);
        if (!"PENDING_PAY".equals(order.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "订单不在待付款状态");
        }
        RechargeDTOs.OrderPayFormVO vo = new RechargeDTOs.OrderPayFormVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setPayForm(buildOrderPagePayForm(order));
        return vo;
    }

    private String buildOrderPagePayForm(TOrder order) {
        AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
        req.setNotifyUrl(alipayConfig.getOrderNotifyUrl());
        String returnUrl = alipayConfig.getOrderReturnUrl();
        if (returnUrl != null) returnUrl = returnUrl.replace("{id}", String.valueOf(order.getId()));
        req.setReturnUrl(returnUrl);

        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", order.getOrderNo());
        biz.put("total_amount", order.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString());
        biz.put("subject", "麦乐迪订单 " + order.getOrderNo());
        biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
        try {
            req.setBizContent(objectMapper.writeValueAsString(biz));
        } catch (Exception e) {
            throw new BizException(ResultCode.PAYMENT_FAILED, "构建支付请求失败");
        }
        try {
            return alipayClient.pageExecute(req).getBody();
        } catch (Exception e) {
            log.error("alipay trade.page.pay(order) failed [{}]: {}", order.getOrderNo(), e.getMessage(), e);
            throw new BizException(ResultCode.PAYMENT_FAILED, "调用支付宝下单失败：" + e.getMessage());
        }
    }

    @Override
    public RechargeDTOs.OrderPayQueryVO queryOrderPay(Long userId, Long orderId) {
        TOrder order = loadUserOrder(orderId, userId);
        RechargeDTOs.OrderPayQueryVO vo = new RechargeDTOs.OrderPayQueryVO();
        vo.setOrderNo(order.getOrderNo());

        // 已付款（待发货/待收货/已完成）：直接返回 PAID
        String st = order.getStatus();
        if ("PENDING_SHIP".equals(st) || "PENDING_RECEIVE".equals(st) || "COMPLETED".equals(st)) {
            vo.setStatus("PAID");
            vo.setPaidAt(order.getPaidAt() == null ? "" : order.getPaidAt().toString());
            return vo;
        }
        if (!"PENDING_PAY".equals(st)) {
            vo.setStatus("PENDING");
            return vo;
        }
        // PENDING_PAY：主动查支付宝
        AlipayTradeQueryResponse resp = doQuery(order.getOrderNo(), order.getTradeNo());
        if (resp != null && resp.isSuccess()
                && ("TRADE_SUCCESS".equals(resp.getTradeStatus())
                    || "TRADE_FINISHED".equals(resp.getTradeStatus()))) {
            orderService.confirmAlipayPaid(order.getOrderNo(), resp.getTradeNo());
            TOrder fresh = orderService.getById(orderId);
            vo.setStatus("PAID");
            vo.setPaidAt(fresh.getPaidAt() == null ? "" : fresh.getPaidAt().toString());
        } else {
            vo.setStatus("PENDING");
        }
        return vo;
    }

    @Override
    public String handleOrderNotify(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "fail";
        try {
            boolean signOk = AlipaySignature.rsaCheckV1(params,
                    alipayConfig.getPublicKey(), alipayConfig.getCharset(), alipayConfig.getSignType());
            if (!signOk) {
                log.warn("alipay order notify 验签失败: out_trade_no={}", params.get("out_trade_no"));
                return "fail";
            }
            String tradeStatus = params.get("trade_status");
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                orderService.confirmAlipayPaid(outTradeNo, tradeNo);
            }
            return "success";
        } catch (Exception e) {
            log.error("alipay order notify 处理异常: {}", e.getMessage(), e);
            return "fail";
        }
    }

    private TOrder loadUserOrder(Long orderId, Long userId) {
        if (orderId == null) throw new BizException(ResultCode.BAD_REQUEST, "缺少订单 ID");
        TOrder o = orderService.getById(orderId);
        if (o == null) throw new BizException(ResultCode.ORDER_NOT_FOUND);
        if (!o.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作该订单");
        }
        return o;
    }
}
