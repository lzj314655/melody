package com.melody.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.TWalletRechargeOrder;
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
 * TWalletRechargeOrderMapper 测试：CRUD + markPaid / markClosed 原子状态流转。
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("TWalletRechargeOrderMapper 集成测试")
class TWalletRechargeOrderMapperTest {

    @Autowired
    private TWalletRechargeOrderMapper rechargeMapper;

    @BeforeEach
    void cleanTable() {
        rechargeMapper.delete(new LambdaQueryWrapper<>());
    }

    private TWalletRechargeOrder seed(String outTradeNo, Long userId, String status) {
        TWalletRechargeOrder o = new TWalletRechargeOrder();
        o.setOutTradeNo(outTradeNo);
        o.setUserId(userId);
        o.setAmount(new BigDecimal("100.00"));
        o.setStatus(status);
        o.setTradeNo(null);
        rechargeMapper.insert(o);
        return o;
    }

    @Test
    @DisplayName("insert + selectById")
    void insertAndSelect() {
        TWalletRechargeOrder o = seed("OTN-001", 1L, "PENDING");
        assertThat(o.getId()).isNotNull();
        TWalletRechargeOrder loaded = rechargeMapper.selectById(o.getId());
        assertThat(loaded.getOutTradeNo()).isEqualTo("OTN-001");
        assertThat(loaded.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("uk_out_trade_no 唯一约束")
    void uniqueOutTradeNo() {
        seed("OTN-DUP", 1L, "PENDING");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> seed("OTN-DUP", 2L, "PENDING")))
                .satisfiesAnyOf(
                        ex -> assertThat(ex).hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class),
                        ex -> assertThat(ex).hasRootCauseInstanceOf(org.springframework.dao.DuplicateKeyException.class),
                        ex -> assertThat(ex).hasMessageContaining("uk_out_trade_no"));
    }

    @Test
    @DisplayName("markPaid PENDING→PAID，回填 trade_no")
    void markPaid_idempotent() {
        seed("OTN-002", 1L, "PENDING");

        int first = rechargeMapper.markPaid("OTN-002", "TRADE-XYZ");
        int second = rechargeMapper.markPaid("OTN-002", "TRADE-XYZ");

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();

        TWalletRechargeOrder loaded = rechargeMapper.selectOne(
                new LambdaQueryWrapper<TWalletRechargeOrder>().eq(TWalletRechargeOrder::getOutTradeNo, "OTN-002"));
        assertThat(loaded.getStatus()).isEqualTo("PAID");
        assertThat(loaded.getTradeNo()).isEqualTo("TRADE-XYZ");
    }

    @Test
    @DisplayName("markPaid 已 PAID 订单不影响")
    void markPaid_ignoresPaidOrder() {
        seed("OTN-003", 1L, "PAID");

        int affected = rechargeMapper.markPaid("OTN-003", "TRADE-NEW");
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("markClosed PENDING→CLOSED")
    void markClosed_onlyPending() {
        seed("OTN-004", 1L, "PENDING");

        int affected = rechargeMapper.markClosed("OTN-004");
        assertThat(affected).isEqualTo(1);

        TWalletRechargeOrder loaded = rechargeMapper.selectOne(
                new LambdaQueryWrapper<TWalletRechargeOrder>().eq(TWalletRechargeOrder::getOutTradeNo, "OTN-004"));
        assertThat(loaded.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("markClosed 不影响 PAID 订单")
    void markClosed_ignoresPaidOrder() {
        seed("OTN-005", 1L, "PAID");

        int affected = rechargeMapper.markClosed("OTN-005");
        assertThat(affected).isZero();

        TWalletRechargeOrder loaded = rechargeMapper.selectOne(
                new LambdaQueryWrapper<TWalletRechargeOrder>().eq(TWalletRechargeOrder::getOutTradeNo, "OTN-005"));
        assertThat(loaded.getStatus()).isEqualTo("PAID");
    }
}
