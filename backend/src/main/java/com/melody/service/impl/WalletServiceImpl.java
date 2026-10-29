package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.exception.BizException;
import com.melody.common.ResultCode;
import com.melody.entity.TWalletAccount;
import com.melody.entity.TWalletLog;
import com.melody.entity.TUser;
import com.melody.mapper.TUserMapper;
import com.melody.mapper.TWalletAccountMapper;
import com.melody.mapper.TWalletLogMapper;
import com.melody.service.IWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements IWalletService {

    private final TWalletAccountMapper walletMapper;
    private final TWalletLogMapper walletLogMapper;
    private final TUserMapper userMapper;

    @Override
    public TWalletAccount getAccount(Long userId) {
        TWalletAccount a = walletMapper.selectOne(new LambdaQueryWrapper<TWalletAccount>()
                .eq(TWalletAccount::getUserId, userId));
        if (a == null) throw new BizException(ResultCode.NOT_FOUND, "钱包账户不存在");
        return a;
    }

    /**
     * 原子增减余额 — 走单条 SQL,天然无并发问题。
     * 下限检查也在 SQL 内做(WHERE 子句),避免应用层竞态。
     *
     * @return 扣减/充值后的余额,失败抛异常
     */
    private BigDecimal atomicUpdateBalance(Long userId, BigDecimal delta, BigDecimal minRequired) {
        int affected = walletMapper.atomicAddBalance(userId, delta, minRequired);
        if (affected == 0) {
            // 区分"账户不存在"和"余额不足"
            TWalletAccount a = walletMapper.selectOne(new LambdaQueryWrapper<TWalletAccount>()
                    .eq(TWalletAccount::getUserId, userId));
            if (a == null) throw new BizException(ResultCode.NOT_FOUND, "钱包账户不存在");
            throw new BizException(ResultCode.WALLET_BALANCE_INSUFFICIENT);
        }
        // 再读一次拿到 after balance
        TWalletAccount a = walletMapper.selectOne(new LambdaQueryWrapper<TWalletAccount>()
                .eq(TWalletAccount::getUserId, userId));
        return a.getBalance() == null ? BigDecimal.ZERO : a.getBalance();
    }

    @Override
    @Transactional
    public void recharge(Long userId, BigDecimal amount, String operator) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "充值金额必须大于 0");
        }
        BigDecimal after = atomicUpdateBalance(userId, amount, null);
        writeLog(userId, amount, after, "RECHARGE", "管理员手动充值", null, operator);
    }

    /** 支付宝网关充值入账：幂等由充值订单表的 PENDING→PAID 控制，这里只管加余额 + 流水 */
    @Override
    @Transactional
    public void rechargeByGateway(Long userId, BigDecimal amount, String outTradeNo) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "充值金额必须大于 0");
        }
        BigDecimal after = atomicUpdateBalance(userId, amount, null);
        writeLog(userId, amount, after, "RECHARGE",
                "支付宝充值 " + (outTradeNo == null ? "" : outTradeNo), null, "alipay");
    }

    /**
     * F-U-045 扣款（订单支付）：返回扣款后的余额
     * 余额不足抛 WALLET_BALANCE_INSUFFICIENT
     */
    @Override
    @Transactional
    public BigDecimal pay(Long userId, BigDecimal amount, Long orderId) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "支付金额不合法");
        }
        // F-A-042: 黑名单用户禁止消费
        TUser u = userMapper.selectById(userId);
        if (u != null && u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被冻结，无法支付");
        }
        // 下限要求 = amount(扣完后不能 < 0,即 balance + (-amount) >= 0 ⇔ balance >= amount)
        BigDecimal after = atomicUpdateBalance(userId, amount.negate(), BigDecimal.ZERO);
        writeLog(userId, amount.negate(), after, "PAY", "订单支付扣款", orderId, "system");
        return after;
    }

    /** F-U-044 退款原路退回钱包 */
    @Override
    @Transactional
    public BigDecimal refund(Long userId, BigDecimal amount, Long orderId) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "退款金额不合法");
        }
        BigDecimal after = atomicUpdateBalance(userId, amount, null);
        writeLog(userId, amount, after, "REFUND", "订单退款退回", orderId, "system");
        return after;
    }

    @Override
    public List<TWalletLog> listLogs(Long userId) {
        return walletLogMapper.selectList(new LambdaQueryWrapper<TWalletLog>()
                .eq(TWalletLog::getUserId, userId)
                .orderByDesc(TWalletLog::getCreatedAt));
    }

    private void writeLog(Long userId, BigDecimal change, BigDecimal after,
                          String type, String reason, Long orderId, String operator) {
        TWalletLog log = new TWalletLog();
        log.setUserId(userId);
        log.setChangeAmount(change);
        log.setBalanceAfter(after);
        log.setType(type);
        log.setReason(reason);
        log.setRelatedOrderId(orderId);
        log.setOperator(operator == null ? "system" : operator);
        walletLogMapper.insert(log);
    }
}