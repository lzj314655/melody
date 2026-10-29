package com.melody.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.TWalletAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TWalletAccountMapper 测试：CRUD + atomicAddBalance（带下限的 CAS 更新）。
 *
 * <p>WalletServiceImpl 的所有余额变更都走 atomicAddBalance，是高并发关键路径。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("TWalletAccountMapper 集成测试")
class TWalletAccountMapperTest {

    @Autowired
    private TWalletAccountMapper walletMapper;

    @BeforeEach
    void cleanTable() {
        walletMapper.delete(new LambdaQueryWrapper<>());
    }

    private TWalletAccount seed(Long userId, String balance) {
        TWalletAccount a = new TWalletAccount();
        a.setUserId(userId);
        a.setBalance(new BigDecimal(balance));
        a.setFrozen(BigDecimal.ZERO);
        a.setPassword("bcrypt");
        walletMapper.insert(a);
        return a;
    }

    @Test
    @DisplayName("CRUD 基本流程")
    void crud() {
        TWalletAccount a = seed(1L, "100.00");

        TWalletAccount loaded = walletMapper.selectById(a.getId());
        assertThat(loaded.getBalance()).isEqualByComparingTo("100.00");

        loaded.setBalance(new BigDecimal("200.00"));
        int upd = walletMapper.updateById(loaded);
        assertThat(upd).isEqualTo(1);

        int del = walletMapper.deleteById(loaded.getId());
        assertThat(del).isEqualTo(1);
        assertThat(walletMapper.selectById(loaded.getId())).isNull();
    }

    @Test
    @DisplayName("uk_user 唯一约束：一个用户只有一个钱包")
    void uniqueUserWallet() {
        seed(10L, "100.00");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> seed(10L, "50.00")))
                .satisfiesAnyOf(
                        ex -> assertThat(ex).hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class),
                        ex -> assertThat(ex).hasRootCauseInstanceOf(org.springframework.dao.DuplicateKeyException.class),
                        ex -> assertThat(ex).hasMessageContaining("uk_wallet_user"));
    }

    @Test
    @DisplayName("atomicAddBalance 正向充值")
    void atomicAdd_recharge() {
        seed(2L, "50.00");
        int affected = walletMapper.atomicAddBalance(2L, new BigDecimal("100.00"), null);
        assertThat(affected).isEqualTo(1);

        TWalletAccount loaded = walletMapper.selectOne(
                new LambdaQueryWrapper<TWalletAccount>().eq(TWalletAccount::getUserId, 2L));
        assertThat(loaded.getBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("atomicAddBalance 负向扣款且满足下限")
    void atomicAdd_payWithinLimit() {
        seed(3L, "100.00");
        // 扣 30，扣完 70 >= 0 → 成功
        int affected = walletMapper.atomicAddBalance(3L, new BigDecimal("-30.00"), BigDecimal.ZERO);
        assertThat(affected).isEqualTo(1);

        TWalletAccount loaded = walletMapper.selectOne(
                new LambdaQueryWrapper<TWalletAccount>().eq(TWalletAccount::getUserId, 3L));
        assertThat(loaded.getBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("atomicAddBalance 余额不足时返回 0")
    void atomicAdd_insufficient() {
        seed(4L, "10.00");
        // 扣 50，要求扣完 >= 0 → 实际扣完 = -40 < 0 → 失败
        int affected = walletMapper.atomicAddBalance(4L, new BigDecimal("-50.00"), BigDecimal.ZERO);
        assertThat(affected).isZero();

        TWalletAccount loaded = walletMapper.selectOne(
                new LambdaQueryWrapper<TWalletAccount>().eq(TWalletAccount::getUserId, 4L));
        // 余额不变
        assertThat(loaded.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("atomicAddBalance 用户不存在时返回 0")
    void atomicAdd_missingUser() {
        int affected = walletMapper.atomicAddBalance(999L, new BigDecimal("10.00"), null);
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("atomicAddBalance 充值场景 minRequired=null 跳过下限检查")
    void atomicAdd_rechargeNoLimit() {
        seed(5L, "0.00");
        // 充值场景：不传下限 → 任何正 delta 都成功
        int affected = walletMapper.atomicAddBalance(5L, new BigDecimal("9999999.00"), null);
        assertThat(affected).isEqualTo(1);

        TWalletAccount loaded = walletMapper.selectOne(
                new LambdaQueryWrapper<TWalletAccount>().eq(TWalletAccount::getUserId, 5L));
        assertThat(loaded.getBalance()).isEqualByComparingTo("9999999.00");
    }
}
