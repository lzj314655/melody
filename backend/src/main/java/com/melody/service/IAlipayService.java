package com.melody.service;

import com.melody.dto.RechargeDTOs;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝充值 Service（沙箱 PC 网页支付 + 主动查询）
 *
 * 流程：
 *  1. createRechargeOrder  —— 本地建 PENDING 订单 + 调 alipay.trade.page.pay 拿支付页表单
 *  2. 前端新窗口打开支付宝付款，当前页轮询 queryOrder
 *  3. queryOrder           —— 已 PAID 直接返回；PENDING 则调 alipay.trade.query，付了就标单+加余额
 *  4. handleNotify         —— 异步通知（验签后标单+加余额），本地沙箱打不到，仅部署后生效
 */
public interface IAlipayService {

    /** 建充值订单并返回支付页表单 */
    RechargeDTOs.RechargeOrderVO createRechargeOrder(Long userId, BigDecimal amount);

    /** 主动查询订单状态，若已付款则入账（幂等） */
    RechargeDTOs.RechargeQueryVO queryOrder(String outTradeNo, Long userId);

    /** 支付宝异步通知处理：验签 + 入账，返回 "success"/"fail" 给支付宝 */
    String handleNotify(Map<String, String> params);

    // ============ 订单支付宝支付（PC 网页支付，复用同一 AlipayClient） ============

    /** 订单支付：生成支付宝支付页表单（out_trade_no 复用 order_no） */
    RechargeDTOs.OrderPayFormVO createOrderPayForm(Long userId, Long orderId);

    /** 订单支付：主动查询，已付款则流转订单 PENDING_PAY→PENDING_SHIP（幂等） */
    RechargeDTOs.OrderPayQueryVO queryOrderPay(Long userId, Long orderId);

    /** 订单异步通知：验签 + 流转订单，返回 "success"/"fail" */
    String handleOrderNotify(Map<String, String> params);
}
