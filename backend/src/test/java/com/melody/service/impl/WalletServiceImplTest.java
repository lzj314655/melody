package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.ResultCode;
import com.melody.entity.TUser;
import com.melody.entity.TWalletAccount;
import com.melody.entity.TWalletLog;
import com.melody.exception.BizException;
import com.melody.mapper.TUserMapper;
import com.melody.mapper.TWalletAccountMapper;
import com.melody.mapper.TWalletLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WalletServiceImpl 单元测试：充值 / 支付 / 退款 + atomicUpdateBalance 各分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletServiceImpl 单元测试")
class WalletServiceImplTest {

    @Mock private TWalletAccountMapper walletMapper;
    @Mock private TWalletLogMapper walletLogMapper;
    @Mock private TUserMapper userMapper;

    @InjectMocks
    private WalletServiceImpl walletService;

    private TWalletAccount wallet(Long userId, String balance) {
        TWalletAccount a = new TWalletAccount();
        a.setUserId(userId);
        a.setBalance(new BigDecimal(balance));
        a.setFrozen(BigDecimal.ZERO);
        return a;
    }

    // ============ getAccount ============

    @Test
    @DisplayName("getAccount 存在 → 返回")
    void getAccount_ok() {
        when(walletMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(wallet(1L, "100.00"));
        assertThat(walletService.getAccount(1L).getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("getAccount 不存在 → NOT_FOUND")
    void getAccount_missing() {
        when(walletMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> walletService.getAccount(99L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    // ============ recharge ============

    @Test
    @DisplayName("recharge 成功 → atomicAdd + 写 RECHARGE 流水")
    void recharge_success() {
        when(walletMapper.atomicAddBalance(eq(1L), eq(new BigDecimal("100")), eq(null))).thenReturn(1);
        when(walletMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(wallet(1L, "200.00"));

        walletService.recharge(1L, new BigDecimal("100"), "admin");

        ArgumentCaptor<TWalletLog> captor = ArgumentCaptor.forClass(TWalletLog.class);
        verify(walletLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("RECHARGE");
        assertThat(captor.getValue().getReason()).isEqualTo("管理员手动充值");
        assertThat(captor.getValue().getOperator()).isEqualTo("admin");
    }

    @Test
    @DisplayName("recharge 金额 ≤ 0 → BAD_REQUEST")
    void recharge_nonPositive() {
        assertThatThrownBy(() -> walletService.recharge(1L, BigDecimal.ZERO, "admin"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
        verify(walletMapper, never()).atomicAddBalance(any(), any(), any());
    }

    @Test
    @DisplayName("rechargeByGateway 操作员记为 alipay")
    void rechargeByGateway_operatorIsAlipay() {
        when(walletMapper.atomicAddBalance(any(), any(), any())).thenReturn(1);
        when(walletMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(wallet(1L, "100"));

        walletService.rechargeByGateway(1L, new BigDecimal("50"), "OTN-001");

        ArgumentCaptor<TWalletLog> captor = ArgumentCaptor.forClass(TWalletLog.class);
        verify(walletLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperator()).isEqualTo("alipay");
        assertThat(captor.getValue().getReason()).contains("OTN-001");
    }

    // ============ pay ============

    @Test
    @DisplayName("pay 成功 → 负数 delta 扣款 + 写 PAY 流水 + 返回 after")
    void pay_success() {
        TUser u = new TUser();
        u.setId(1L);
        u.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(u);
        when(walletMapper.atomicAddBalance(eq(1L), eq(new BigDecimal("-30")), eq(BigDecimal.ZERO))).thenReturn(1);
        when(walletMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(wallet(1L, "70.00"));

        BigDecimal after = walletService.pay(1L, new BigDecimal("30"), 999L);

        assertThat(after).isEqualByComparingTo("70.00");
        ArgumentCaptor<TWalletLog> captor = ArgumentCaptor.forClass(TWalletLog.class);
        verify(walletLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("PAY");
        assertThat(captor.getValue().getChangeAmount()).isEqualByComparingTo("-30");
        assertThat(captor.getValue().getRelatedOrderId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("pay 黑名单用户 → FORBIDDEN")
    void pay_blacklisted() {
        TUser u = new TUser();
        u.setId(1L);
        u.setStatus(0);
        when(userMapper.selectById(1L)).thenReturn(u);

        assertThatThrownBy(() -> walletService.pay(1L, new BigDecimal("30"), 999L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("pay 余额不足 → atomicUpdateBalance 返 0 → WALLET_BALANCE_INSUFFICIENT")
    void pay_insufficientBalance() {
        TUser u = new TUser();
        u.setId(1L);
        u.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(u);
        when(walletMapper.atomicAddBalance(any(), any(), any())).thenReturn(0);
        when(walletMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(wallet(1L, "5.00"));

        assertThatThrownBy(() -> walletService.pay(1L, new BigDecimal("30"), 999L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.WALLET_BALANCE_INSUFFICIENT.getCode()));
    }

    @Test
    @DisplayName("pay 用户不存在 → atomic 返 0 + 钱包也不存在 → NOT_FOUND")
    void pay_userAndWalletMissing() {
        when(userMapper.selectById(99L)).thenReturn(null);
        when(walletMapper.atomicAddBalance(any(), any(), any())).thenReturn(0);
        when(walletMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> walletService.pay(99L, new BigDecimal("30"), 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    // ============ refund ============

    @Test
    @DisplayName("refund 成功 → 正 delta 加余额")
    void refund_success() {
        when(walletMapper.atomicAddBalance(eq(1L), eq(new BigDecimal("30")), eq(null))).thenReturn(1);
        when(walletMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(wallet(1L, "130.00"));

        BigDecimal after = walletService.refund(1L, new BigDecimal("30"), 999L);
        assertThat(after).isEqualByComparingTo("130.00");

        ArgumentCaptor<TWalletLog> captor = ArgumentCaptor.forClass(TWalletLog.class);
        verify(walletLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("REFUND");
    }

    @Test
    @DisplayName("refund 金额 ≤ 0 → BAD_REQUEST")
    void refund_nonPositive() {
        assertThatThrownBy(() -> walletService.refund(1L, BigDecimal.ZERO, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
    }

    // ============ listLogs ============

    @Test
    @DisplayName("listLogs 按 user_id + createdAt desc")
    void listLogs() {
        when(walletLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThat(walletService.listLogs(1L)).isEmpty();
    }
}
