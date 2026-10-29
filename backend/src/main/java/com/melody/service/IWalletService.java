package com.melody.service;

import com.melody.entity.TWalletAccount;
import com.melody.entity.TWalletLog;

import java.math.BigDecimal;
import java.util.List;

/**
 * 钱包 Service（F-C-003 虚拟钱包）
 *
 * PRD §7.6：仅后台手动充值；退款退回钱包；不支持提现
 */
public interface IWalletService {

    TWalletAccount getAccount(Long userId);

    /** F-C-003 充值（管理员手动） */
    void recharge(Long userId, BigDecimal amount, String operator);

    /** 支付宝网关充值入账（标单后调用，原子加余额 + 写流水，备注带 out_trade_no） */
    void rechargeByGateway(Long userId, BigDecimal amount, String outTradeNo);

    /** F-U-045 下单扣款 */
    BigDecimal pay(Long userId, BigDecimal amount, Long orderId);

    /** F-U-044 退款原路退回钱包 */
    BigDecimal refund(Long userId, BigDecimal amount, Long orderId);

    List<TWalletLog> listLogs(Long userId);
}