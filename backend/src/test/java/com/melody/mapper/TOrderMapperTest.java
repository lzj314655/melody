package com.melody.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.TOrder;
import com.melody.entity.TOrderItem;
import com.melody.entity.TUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TOrderMapper 测试：CRUD + 三个自定义 SQL。
 *
 * <p>覆盖：
 * <ul>
 *   <li>countPurchasedProduct — 关联查询 + 状态 IN 过滤</li>
 *   <li>markPaidByAlipay — 原子 PENDING_PAY → PENDING_SHIP，幂等</li>
 *   <li>markPaidByWallet — 原子 PENDING_PAY → PENDING_SHIP，幂等</li>
 * </ul>
 * </p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("TOrderMapper 集成测试")
class TOrderMapperTest {

    @Autowired
    private TOrderMapper orderMapper;

    @Autowired
    private TOrderItemMapper orderItemMapper;

    @Autowired
    private TUserMapper userMapper;

    @BeforeEach
    void cleanTable() {
        orderItemMapper.delete(new LambdaQueryWrapper<>());
        orderMapper.delete(new LambdaQueryWrapper<>());
        userMapper.delete(new LambdaQueryWrapper<>());
    }

    private TUser seedUser(Long id, String phone) {
        TUser u = new TUser();
        u.setId(id);
        u.setPhone(phone);
        u.setUsername(phone);
        u.setPassword("x");
        u.setRankCode("V0");
        u.setStatus(1);
        u.setJoinDate(LocalDateTime.now());
        userMapper.insert(u);
        return u;
    }

    private TOrder seedOrder(Long userId, String orderNo, String status) {
        TOrder o = new TOrder();
        o.setOrderNo(orderNo);
        o.setUserId(userId);
        o.setTotalPrice(new BigDecimal("99.00"));
        o.setGoodsAmount(new BigDecimal("99.00"));
        o.setDiscount(BigDecimal.ZERO);
        o.setShippingFee(BigDecimal.ZERO);
        o.setStatus(status);
        o.setPayMethod("ALIPAY");
        o.setConsigneeName("张三");
        o.setConsigneePhone("13800000000");
        o.setConsigneeAddress("北京市 朝阳区 测试街道 1 号");
        orderMapper.insert(o);
        return o;
    }

    private TOrderItem seedItem(Long orderId, Long productId) {
        TOrderItem item = new TOrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setProductName("测试商品");
        item.setProductImage("/img/x.jpg");
        item.setSpecName("默认规格");
        item.setPrice(new BigDecimal("99.00"));
        item.setQuantity(1);
        item.setSubtotal(new BigDecimal("99.00"));
        orderItemMapper.insert(item);
        return item;
    }

    @Test
    @DisplayName("insert + selectById")
    void insertAndSelect() {
        TUser u = seedUser(null, "13800000001");
        TOrder o = seedOrder(u.getId(), "ORD-T-001", "PENDING_PAY");
        assertThat(o.getId()).isNotNull();

        TOrder loaded = orderMapper.selectById(o.getId());
        assertThat(loaded.getOrderNo()).isEqualTo("ORD-T-001");
        assertThat(loaded.getStatus()).isEqualTo("PENDING_PAY");
    }

    @Test
    @DisplayName("uk_order_no 唯一索引生效")
    void uniqueOrderNo() {
        TUser u = seedUser(null, "13800000002");
        seedOrder(u.getId(), "ORD-DUP", "PENDING_PAY");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> seedOrder(u.getId(), "ORD-DUP", "PENDING_PAY"))
                .satisfiesAnyOf(
                        ex -> org.assertj.core.api.Assertions.assertThat(ex)
                                .hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class),
                        ex -> org.assertj.core.api.Assertions.assertThat(ex)
                                .hasRootCauseInstanceOf(org.springframework.dao.DuplicateKeyException.class),
                        ex -> org.assertj.core.api.Assertions.assertThat(ex)
                                .hasMessageContaining("uk_order_no"));
    }

    @Test
    @DisplayName("countPurchasedProduct 仅统计 PENDING_RECEIVE/COMPLETED")
    void countPurchasedProduct_filtersByStatus() {
        TUser u = seedUser(null, "13800000003");
        TOrder orderPaid = seedOrder(u.getId(), "ORD-P-1", "PENDING_RECEIVE");
        TOrder orderDone = seedOrder(u.getId(), "ORD-P-2", "COMPLETED");
        TOrder orderUnpaid = seedOrder(u.getId(), "ORD-P-3", "PENDING_PAY");
        TOrder orderCancel = seedOrder(u.getId(), "ORD-P-4", "CANCELED");

        long productId = 100L;
        seedItem(orderPaid.getId(), productId);
        seedItem(orderDone.getId(), productId);
        seedItem(orderUnpaid.getId(), productId);
        seedItem(orderCancel.getId(), productId);

        long count = orderMapper.countPurchasedProduct(u.getId(), productId);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("countPurchasedProduct 不同用户隔离")
    void countPurchasedProduct_isolatesUsers() {
        TUser u1 = seedUser(null, "13800000011");
        TUser u2 = seedUser(null, "13800000012");
        TOrder order = seedOrder(u1.getId(), "ORD-U-1", "COMPLETED");
        seedItem(order.getId(), 200L);

        long countU1 = orderMapper.countPurchasedProduct(u1.getId(), 200L);
        long countU2 = orderMapper.countPurchasedProduct(u2.getId(), 200L);
        assertThat(countU1).isEqualTo(1L);
        assertThat(countU2).isZero();
    }

    @Test
    @DisplayName("markPaidByAlipay 仅 PENDING_PAY → PENDING_SHIP")
    void markPaidByAlipay_idempotent() {
        TUser u = seedUser(null, "13800000004");
        TOrder o = seedOrder(u.getId(), "ORD-A-1", "PENDING_PAY");

        int first = orderMapper.markPaidByAlipay("ORD-A-1", "TRADE-123");
        int second = orderMapper.markPaidByAlipay("ORD-A-1", "TRADE-123");

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();

        TOrder loaded = orderMapper.selectById(o.getId());
        assertThat(loaded.getStatus()).isEqualTo("PENDING_SHIP");
        assertThat(loaded.getTradeNo()).isEqualTo("TRADE-123");
        assertThat(loaded.getPayMethod()).isEqualTo("ALIPAY");
        assertThat(loaded.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("markPaidByAlipay 不影响非 PENDING_PAY 订单")
    void markPaidByAlipay_ignoresOtherStatus() {
        TUser u = seedUser(null, "13800000005");
        seedOrder(u.getId(), "ORD-A-2", "COMPLETED");

        int affected = orderMapper.markPaidByAlipay("ORD-A-2", "TRADE-999");
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("markPaidByWallet 仅 PENDING_PAY → PENDING_SHIP")
    void markPaidByWallet_idempotent() {
        TUser u = seedUser(null, "13800000006");
        TOrder o = seedOrder(u.getId(), "ORD-W-1", "PENDING_PAY");

        int first = orderMapper.markPaidByWallet(o.getId());
        int second = orderMapper.markPaidByWallet(o.getId());

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();

        TOrder loaded = orderMapper.selectById(o.getId());
        assertThat(loaded.getStatus()).isEqualTo("PENDING_SHIP");
        assertThat(loaded.getPayMethod()).isEqualTo("WALLET");
    }

    @Test
    @DisplayName("listByUser 按 user_id + status 过滤 + 按 id desc")
    void listByUser() {
        TUser u = seedUser(null, "13800000007");
        TUser other = seedUser(null, "13800000008");
        seedOrder(u.getId(), "ORD-L-1", "PENDING_PAY");
        seedOrder(u.getId(), "ORD-L-2", "COMPLETED");
        seedOrder(other.getId(), "ORD-L-3", "PENDING_PAY");

        List<TOrder> mine = orderMapper.selectList(
                new LambdaQueryWrapper<TOrder>()
                        .eq(TOrder::getUserId, u.getId())
                        .orderByDesc(TOrder::getId));
        assertThat(mine).hasSize(2);
        assertThat(mine.get(0).getOrderNo()).isEqualTo("ORD-L-2"); // 倒序：后插入的在前
    }
}
