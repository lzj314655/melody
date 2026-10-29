package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.ResultCode;
import com.melody.entity.TCartItem;
import com.melody.entity.TCoupon;
import com.melody.entity.TOrder;
import com.melody.entity.TProduct;
import com.melody.entity.TProductSku;
import com.melody.entity.TUser;
import com.melody.entity.TUserAddress;
import com.melody.entity.TUserCoupon;
import com.melody.exception.BizException;
import com.melody.mapper.TCartItemMapper;
import com.melody.mapper.TCouponMapper;
import com.melody.mapper.TGrowthLogMapper;
import com.melody.mapper.TOrderItemMapper;
import com.melody.mapper.TOrderMapper;
import com.melody.mapper.TPointLogMapper;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.TProductSkuMapper;
import com.melody.mapper.TUserCouponMapper;
import com.melody.mapper.TUserMapper;
import com.melody.service.ICouponService;
import com.melody.service.IWalletService;
import com.melody.service.OrderNotifyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceImpl 单元测试 — 覆盖核心订单流程。
 *
 * <p>create 是最复杂的流程：黑名单拦截、库存校验、优惠券计算（满减 vs 折扣）、
 * WALLET 即时扣款、ALIPAY 保持 PENDING_PAY、订单号冲突重试等。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl 单元测试")
class OrderServiceImplTest {

    @Mock private TOrderMapper orderMapper;
    @Mock private TOrderItemMapper orderItemMapper;
    @Mock private TCartItemMapper cartMapper;
    @Mock private TProductMapper productMapper;
    @Mock private TProductSkuMapper skuMapper;
    @Mock private TCouponMapper couponMapper;
    @Mock private TUserCouponMapper userCouponMapper;
    @Mock private TPointLogMapper pointLogMapper;
    @Mock private TGrowthLogMapper growthLogMapper;
    @Mock private IWalletService walletService;
    @Mock private ICouponService couponService;
    @Mock private TUserMapper userMapper;
    @Mock private OrderNotifyService orderNotifyService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private TUser newUser(Long id, int status) {
        TUser u = new TUser();
        u.setId(id);
        u.setPhone("13800000001");
        u.setStatus(status);
        u.setGrowthValue(0);
        u.setRankCode("V0");
        return u;
    }

    private TCartItem cartItem(Long id, Long userId, Long productId, Long skuId, int qty) {
        TCartItem i = new TCartItem();
        i.setId(id);
        i.setUserId(userId);
        i.setProductId(productId);
        i.setSkuId(skuId);
        i.setQuantity(qty);
        i.setPrice(new BigDecimal("100.00"));
        i.setSpecName("默认");
        i.setSelected(1);
        return i;
    }

    private TProduct newProduct(Long id, int status, int stock) {
        TProduct p = new TProduct();
        p.setId(id);
        p.setName("测试商品");
        p.setCoverImage("/img/x.jpg");
        p.setStatus(status);
        p.setInventory(stock);
        p.setSales(0);
        p.setPrice(new BigDecimal("100.00"));
        return p;
    }

    private TProductSku newSku(Long id, Long productId, int stock) {
        TProductSku s = new TProductSku();
        s.setId(id);
        s.setProductId(productId);
        s.setSpecName("默认");
        s.setPrice(new BigDecimal("100.00"));
        s.setStock(stock);
        s.setStatus(1);
        return s;
    }

    private TUserAddress address() {
        TUserAddress a = new TUserAddress();
        a.setId(1L);
        a.setUserId(1L);
        a.setName("张三");
        a.setPhone("13800000000");
        a.setProvince("北京市");
        a.setCity("朝阳区");
        a.setDistrict("xx");
        a.setDetail("xx街道1号");
        return a;
    }

    // ============ create ============

    @Nested
    @DisplayName("create 流程")
    class Create {

        @BeforeEach
        void initTx() {
            // 模拟事务上下文，使 TransactionSynchronizationManager.registerSynchronization 不抛异常
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.initSynchronization();
            }
        }

        @AfterEach
        void clearTx() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clear();
            }
        }

        @Test
        @DisplayName("cartItemIds 空 → CART_EMPTY")
        void cartEmpty() {
            assertThatThrownBy(() -> orderService.create(1L, null, address(), null, "WALLET"))
                    .isInstanceOf(BizException.class)
                    .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.CART_EMPTY.getCode()));
        }

        @Test
        @DisplayName("用户不存在 → USER_NOT_FOUND")
        void userMissing() {
            when(userMapper.selectById(1L)).thenReturn(null);
            assertThatThrownBy(() -> orderService.create(1L, List.of(1L), address(), null, "WALLET"))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("黑名单用户 → FORBIDDEN")
        void blacklisted() {
            when(userMapper.selectById(1L)).thenReturn(newUser(1L, 0));
            assertThatThrownBy(() -> orderService.create(1L, List.of(1L), address(), null, "WALLET"))
                    .isInstanceOf(BizException.class)
                    .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
        }

        @Test
        @DisplayName("商品下架 → PRODUCT_OFF_SHELF")
        void productOffShelf() {
            when(userMapper.selectById(1L)).thenReturn(newUser(1L, 1));
            when(cartMapper.selectBatchIds(List.of(1L))).thenReturn(new ArrayList<>(List.of(cartItem(1L, 1L, 100L, 10L, 1))));
            TProduct p = newProduct(100L, 0, 10); // status=0 下架
            when(productMapper.selectById(100L)).thenReturn(p);

            assertThatThrownBy(() -> orderService.create(1L, List.of(1L), address(), null, "WALLET"))
                    .isInstanceOf(BizException.class)
                    .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.PRODUCT_OFF_SHELF.getCode()));
        }

        @Test
        @DisplayName("SKU 库存不足 → PRODUCT_OUT_OF_STOCK")
        void skuOutOfStock() {
            when(userMapper.selectById(1L)).thenReturn(newUser(1L, 1));
            when(cartMapper.selectBatchIds(List.of(1L))).thenReturn(new ArrayList<>(List.of(cartItem(1L, 1L, 100L, 10L, 5))));
            when(productMapper.selectById(100L)).thenReturn(newProduct(100L, 1, 100));
            when(skuMapper.selectById(10L)).thenReturn(newSku(10L, 100L, 2)); // 库存 2 < 需要 5

            assertThatThrownBy(() -> orderService.create(1L, List.of(1L), address(), null, "WALLET"))
                    .isInstanceOf(BizException.class)
                    .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.PRODUCT_OUT_OF_STOCK.getCode()));
        }

        @Test
        @DisplayName("WALLET 成功 → 即时扣款 + 状态 PENDING_SHIP + 加积分成长")
        void wallet_success() {
            when(userMapper.selectById(1L)).thenReturn(newUser(1L, 1));
            when(cartMapper.selectBatchIds(List.of(1L))).thenReturn(new ArrayList<>(List.of(cartItem(1L, 1L, 100L, 10L, 1))));
            when(productMapper.selectById(100L)).thenReturn(newProduct(100L, 1, 100));
            when(skuMapper.selectById(10L)).thenReturn(newSku(10L, 100L, 100));
            when(orderMapper.insert(any(TOrder.class))).thenAnswer(inv -> {
                TOrder arg = inv.getArgument(0);
                arg.setId(999L);
                return 1;
            });

            TOrder result = orderService.create(1L, List.of(1L), address(), null, "WALLET");

            assertThat(result.getStatus()).isEqualTo("PENDING_SHIP");
            assertThat(result.getPayMethod()).isEqualTo("WALLET");
            assertThat(result.getPaidAt()).isNotNull();
            verify(walletService).pay(eq(1L), eq(new BigDecimal("100.00")), eq(999L));
            verify(pointLogMapper, times(1)).insert(any());
            verify(growthLogMapper, times(1)).insert(any());
            // 清空购物车
            verify(cartMapper).deleteById(1L);
        }

        @Test
        @DisplayName("ALIPAY → 保持 PENDING_PAY + 不扣钱包 + 不加积分")
        void alipay_pendingPay() {
            when(userMapper.selectById(1L)).thenReturn(newUser(1L, 1));
            when(cartMapper.selectBatchIds(List.of(1L))).thenReturn(new ArrayList<>(List.of(cartItem(1L, 1L, 100L, 10L, 1))));
            when(productMapper.selectById(100L)).thenReturn(newProduct(100L, 1, 100));
            when(skuMapper.selectById(10L)).thenReturn(newSku(10L, 100L, 100));
            when(orderMapper.insert(any(TOrder.class))).thenAnswer(inv -> {
                TOrder arg = inv.getArgument(0);
                arg.setId(999L);
                return 1;
            });

            TOrder result = orderService.create(1L, List.of(1L), address(), null, "ALIPAY");

            assertThat(result.getStatus()).isEqualTo("PENDING_PAY");
            assertThat(result.getPayMethod()).isEqualTo("ALIPAY");
            assertThat(result.getPaidAt()).isNull();
            verify(walletService, never()).pay(any(), any(), any());
            verify(pointLogMapper, never()).insert(any());
        }

        @Test
        @DisplayName("满减券：商品 100 / 满减 10 → total=90")
        void fullCutCoupon() {
            when(userMapper.selectById(1L)).thenReturn(newUser(1L, 1));
            when(cartMapper.selectBatchIds(List.of(1L))).thenReturn(new ArrayList<>(List.of(cartItem(1L, 1L, 100L, 10L, 1))));
            when(productMapper.selectById(100L)).thenReturn(newProduct(100L, 1, 100));
            when(skuMapper.selectById(10L)).thenReturn(newSku(10L, 100L, 100));

            TUserCoupon uc = new TUserCoupon();
            uc.setId(5L);
            uc.setUserId(1L);
            uc.setCouponId(50L);
            uc.setStatus("可用");
            when(userCouponMapper.selectById(5L)).thenReturn(uc);

            TCoupon c = new TCoupon();
            c.setId(50L);
            c.setType("满减");
            c.setDiscount(new BigDecimal("10"));
            c.setMinSpend(new BigDecimal("100"));
            when(couponMapper.selectById(50L)).thenReturn(c);

            when(orderMapper.insert(any(TOrder.class))).thenAnswer(inv -> {
                TOrder arg = inv.getArgument(0);
                arg.setId(999L);
                return 1;
            });

            TOrder result = orderService.create(1L, List.of(1L), address(), 5L, "WALLET");

            assertThat(result.getDiscount()).isEqualByComparingTo("10");
            assertThat(result.getTotalPrice()).isEqualByComparingTo("90");
        }

        @Test
        @DisplayName("折扣券 0.8 + 商品 100 → 折扣 20（封顶 50）")
        void discountCoupon_cappedAt50() {
            when(userMapper.selectById(1L)).thenReturn(newUser(1L, 1));
            when(cartMapper.selectBatchIds(List.of(1L))).thenReturn(new ArrayList<>(List.of(cartItem(1L, 1L, 100L, 10L, 1))));
            when(productMapper.selectById(100L)).thenReturn(newProduct(100L, 1, 100));
            when(skuMapper.selectById(10L)).thenReturn(newSku(10L, 100L, 100));

            TUserCoupon uc = new TUserCoupon();
            uc.setId(5L);
            uc.setUserId(1L);
            uc.setCouponId(50L);
            uc.setStatus("可用");
            when(userCouponMapper.selectById(5L)).thenReturn(uc);

            TCoupon c = new TCoupon();
            c.setId(50L);
            c.setType("折扣");
            c.setDiscount(new BigDecimal("0.8"));
            c.setMinSpend(BigDecimal.ZERO);
            when(couponMapper.selectById(50L)).thenReturn(c);

            when(orderMapper.insert(any(TOrder.class))).thenAnswer(inv -> {
                TOrder arg = inv.getArgument(0);
                arg.setId(999L);
                return 1;
            });

            TOrder result = orderService.create(1L, List.of(1L), address(), 5L, "WALLET");

            // 100 * (1 - 0.8) = 20 < 50 → 不封顶
            assertThat(result.getDiscount()).isEqualByComparingTo("20");
        }

        @Test
        @DisplayName("折扣券大额 → 封顶 50")
        void discountCoupon_capTo50() {
            when(userMapper.selectById(1L)).thenReturn(newUser(1L, 1));
            // 1000 元商品
            TCartItem bigItem = cartItem(1L, 1L, 100L, 10L, 10);
            bigItem.setPrice(new BigDecimal("100.00"));
            when(cartMapper.selectBatchIds(List.of(1L))).thenReturn(new ArrayList<>(List.of(bigItem)));
            when(productMapper.selectById(100L)).thenReturn(newProduct(100L, 1, 100));
            when(skuMapper.selectById(10L)).thenReturn(newSku(10L, 100L, 100));

            TUserCoupon uc = new TUserCoupon();
            uc.setId(5L);
            uc.setUserId(1L);
            uc.setCouponId(50L);
            uc.setStatus("可用");
            when(userCouponMapper.selectById(5L)).thenReturn(uc);

            TCoupon c = new TCoupon();
            c.setId(50L);
            c.setType("折扣");
            c.setDiscount(new BigDecimal("0.5")); // 5 折
            c.setMinSpend(BigDecimal.ZERO);
            when(couponMapper.selectById(50L)).thenReturn(c);

            when(orderMapper.insert(any(TOrder.class))).thenAnswer(inv -> {
                TOrder arg = inv.getArgument(0);
                arg.setId(999L);
                return 1;
            });

            TOrder result = orderService.create(1L, List.of(1L), address(), 5L, "WALLET");
            // 1000 * 0.5 = 500 → 封顶 50
            assertThat(result.getDiscount()).isEqualByComparingTo("50");
        }

        @Test
        @DisplayName("未达优惠券门槛 → BAD_REQUEST")
        void couponMinSpendNotMet() {
            when(userMapper.selectById(1L)).thenReturn(newUser(1L, 1));
            when(cartMapper.selectBatchIds(List.of(1L))).thenReturn(new ArrayList<>(List.of(cartItem(1L, 1L, 100L, 10L, 1))));
            when(productMapper.selectById(100L)).thenReturn(newProduct(100L, 1, 100));
            when(skuMapper.selectById(10L)).thenReturn(newSku(10L, 100L, 100));

            TUserCoupon uc = new TUserCoupon();
            uc.setId(5L);
            uc.setStatus("可用");
            when(userCouponMapper.selectById(5L)).thenReturn(uc);

            TCoupon c = new TCoupon();
            c.setMinSpend(new BigDecimal("500")); // 门槛 500
            when(couponMapper.selectById(any())).thenReturn(c);

            assertThatThrownBy(() -> orderService.create(1L, List.of(1L), address(), 5L, "WALLET"))
                    .isInstanceOf(BizException.class);
        }
    }

    // ============ payByWallet ============

    @Test
    @DisplayName("payByWallet PENDING_PAY → 钱包扣款 + 加积分")
    void payByWallet_success() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(1L);
        o.setStatus("PENDING_PAY");
        o.setTotalPrice(new BigDecimal("100"));
        when(orderMapper.selectById(1L)).thenReturn(o);
        when(orderMapper.markPaidByWallet(1L)).thenReturn(1);
        when(orderMapper.selectById(1L)).thenReturn(o);

        TOrder result = orderService.payByWallet(1L, 1L);

        verify(walletService).pay(1L, new BigDecimal("100"), 1L);
        verify(pointLogMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("payByWallet 非 PENDING_PAY → ORDER_STATUS_INVALID")
    void payByWallet_wrongStatus() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(1L);
        o.setStatus("PENDING_SHIP");
        when(orderMapper.selectById(1L)).thenReturn(o);

        assertThatThrownBy(() -> orderService.payByWallet(1L, 1L))
                .isInstanceOf(BizException.class);
        verify(walletService, never()).pay(any(), any(), any());
    }

    @Test
    @DisplayName("payByWallet 他人订单 → FORBIDDEN")
    void payByWallet_notOwner() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(2L);
        o.setStatus("PENDING_PAY");
        when(orderMapper.selectById(1L)).thenReturn(o);

        assertThatThrownBy(() -> orderService.payByWallet(1L, 1L))
                .isInstanceOf(BizException.class);
    }

    // ============ confirmAlipayPaid ============

    @Test
    @DisplayName("confirmAlipayPaid affected=1 → 加积分；affected=0 → 幂等跳过")
    void confirmAlipayPaid_idempotent() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(1L);
        o.setOrderNo("ORD-001");
        o.setTotalPrice(new BigDecimal("100"));
        when(orderMapper.markPaidByAlipay("ORD-001", "TRADE-1")).thenReturn(1);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(o);

        orderService.confirmAlipayPaid("ORD-001", "TRADE-1");

        verify(pointLogMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("confirmAlipayPaid affected=0 → 跳过")
    void confirmAlipayPaid_affectedZero() {
        when(orderMapper.markPaidByAlipay("ORD-001", "TRADE-1")).thenReturn(0);

        orderService.confirmAlipayPaid("ORD-001", "TRADE-1");

        verify(pointLogMapper, never()).insert(any());
    }

    // ============ cancel / confirmDelivery / refund ============

    @Test
    @DisplayName("cancel PENDING_PAY → 取消 + 状态 CANCELED")
    void cancel_success() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(1L);
        o.setStatus("PENDING_PAY");
        when(orderMapper.selectById(1L)).thenReturn(o);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>(List.of()));

        orderService.cancel(1L, 1L);

        assertThat(o.getStatus()).isEqualTo("CANCELED");
        verify(orderMapper).updateById(o);
    }

    @Test
    @DisplayName("cancel 非 PENDING_PAY → ORDER_STATUS_INVALID")
    void cancel_wrongStatus() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(1L);
        o.setStatus("COMPLETED");
        when(orderMapper.selectById(1L)).thenReturn(o);

        assertThatThrownBy(() -> orderService.cancel(1L, 1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("confirmDelivery PENDING_RECEIVE → COMPLETED")
    void confirmDelivery_success() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(1L);
        o.setStatus("PENDING_RECEIVE");
        when(orderMapper.selectById(1L)).thenReturn(o);

        orderService.confirmDelivery(1L, 1L);

        assertThat(o.getStatus()).isEqualTo("COMPLETED");
        assertThat(o.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("refund PENDING_RECEIVE/COMPLETED → AFTER_SALE")
    void refund_success() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(1L);
        o.setStatus("COMPLETED");
        when(orderMapper.selectById(1L)).thenReturn(o);

        orderService.refund(1L, 1L, "质量问题");

        assertThat(o.getStatus()).isEqualTo("AFTER_SALE");
        assertThat(o.getRefundReason()).isEqualTo("质量问题");
    }

    @Test
    @DisplayName("refund 不在允许状态 → ORDER_STATUS_INVALID")
    void refund_wrongStatus() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(1L);
        o.setStatus("PENDING_PAY");
        when(orderMapper.selectById(1L)).thenReturn(o);

        assertThatThrownBy(() -> orderService.refund(1L, 1L, "x"))
                .isInstanceOf(BizException.class);
    }

    // ============ ship / approveRefund ============

    @Test
    @DisplayName("ship PENDING_SHIP → PENDING_RECEIVE + 写 tracking")
    void ship_success() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(1L);
        o.setStatus("PENDING_SHIP");
        when(orderMapper.selectById(1L)).thenReturn(o);

        orderService.ship(1L, "SF12345");

        assertThat(o.getStatus()).isEqualTo("PENDING_RECEIVE");
        assertThat(o.getTrackingNo()).isEqualTo("SF12345");
        assertThat(o.getShippedAt()).isNotNull();
    }

    @Test
    @DisplayName("approveRefund AFTER_SALE → 钱包退款 + REFUNDED")
    void approveRefund_success() {
        TOrder o = new TOrder();
        o.setId(1L);
        o.setUserId(1L);
        o.setTotalPrice(new BigDecimal("100"));
        o.setStatus("AFTER_SALE");
        when(orderMapper.selectById(1L)).thenReturn(o);

        orderService.approveRefund(1L);

        verify(walletService).refund(1L, new BigDecimal("100"), 1L);
        assertThat(o.getStatus()).isEqualTo("REFUNDED");
    }

    // ============ listByUser / listAll ============

    @Test
    @DisplayName("listByUser ALL → 不加 status 过滤")
    void listByUserAll() {
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>(List.of()));
        assertThat(orderService.listByUser(1L, "ALL")).isEmpty();
    }

    @Test
    @DisplayName("listAll 加 status 过滤")
    void listAll() {
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>(List.of()));
        assertThat(orderService.listAll("PENDING_PAY")).isEmpty();
    }

    @Test
    @DisplayName("getById 不存在 → ORDER_NOT_FOUND")
    void getById_missing() {
        when(orderMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> orderService.getById(99L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.ORDER_NOT_FOUND.getCode()));
    }
}
