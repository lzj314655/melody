package com.melody.controller;

import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.dto.RechargeDTOs;
import com.melody.entity.TWalletAccount;
import com.melody.entity.TWalletLog;
import com.melody.service.IAlipayService;
import com.melody.service.IWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 钱包接口（PRD §8.1 /api/wallet）
 *
 * 充值走支付宝沙箱：先创建充值单拿支付页表单，用户付款后前端轮询 query，
 * 由支付宝异步通知 / 主动查询确认入账。**不存在“直接加余额”的接口**，
 * 否则任何登录用户都能凭空造钱。
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final IWalletService walletService;
    private final IAlipayService alipayService;

    /** 查当前用户钱包余额 */
    @GetMapping
    public R<TWalletAccount> get() {
        return R.ok(walletService.getAccount(SecurityUtils.currentId()));
    }

    /** 钱包流水 */
    @GetMapping("/logs")
    public R<List<TWalletLog>> logs() {
        return R.ok(walletService.listLogs(SecurityUtils.currentId()));
    }

    /** 创建支付宝充值单，返回支付页表单（前端新窗口打开） */
    @PostMapping("/recharge")
    public R<RechargeDTOs.RechargeOrderVO> recharge(@RequestParam BigDecimal amount) {
        return R.ok(alipayService.createRechargeOrder(SecurityUtils.currentId(), amount));
    }

    /** 轮询充值结果：已付款则入账并返回最新余额 */
    @GetMapping("/recharge/query")
    public R<RechargeDTOs.RechargeQueryVO> query(@RequestParam String outTradeNo) {
        return R.ok(alipayService.queryOrder(outTradeNo, SecurityUtils.currentId()));
    }

    /** 支付宝异步通知（公开，验签后入账；本地沙箱打不到，靠主动查询兜底） */
    @PostMapping(value = "/recharge/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(@RequestParam Map<String, String> params) {
        return alipayService.handleNotify(params);
    }
}
