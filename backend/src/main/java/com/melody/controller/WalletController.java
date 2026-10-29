package com.melody.controller;

import com.melody.common.R;
import com.melody.common.ResultCode;
import com.melody.common.SecurityUtils;
import com.melody.entity.TWalletAccount;
import com.melody.entity.TWalletLog;
import com.melody.exception.BizException;
import com.melody.service.IWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 钱包接口（PRD §8.1 /api/wallet）
 *
 * 充值：演示环境直接入账（已去掉支付宝沙箱支付），订单支付仍支持余额 / 沙箱双通道。
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final IWalletService walletService;

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

    /** 充值：直接入账（去掉支付宝沙箱支付），成功返回最新钱包账户 */
    @PostMapping("/recharge")
    public R<TWalletAccount> recharge(@RequestParam BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "充值金额必须大于 0");
        }
        walletService.recharge(SecurityUtils.currentId(), amount, "USER_RECHARGE");
        return R.ok(walletService.getAccount(SecurityUtils.currentId()));
    }
}
