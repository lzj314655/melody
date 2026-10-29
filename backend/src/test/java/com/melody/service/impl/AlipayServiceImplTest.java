package com.melody.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.ResultCode;
import com.melody.config.AlipayConfig;
import com.melody.dto.RechargeDTOs;
import com.melody.entity.TOrder;
import com.melody.entity.TWalletRechargeOrder;
import com.melody.exception.BizException;
import com.melody.mapper.TWalletRechargeOrderMapper;
import com.melody.service.IOrderService;
import com.melody.service.IWalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AlipayServiceImpl 单元测试 — 主要验证建单 / 状态查询 / 边界条件；
 * 涉及 alipayClient 的部分用 mock 跳过真实调用。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlipayServiceImpl 单元测试")
class AlipayServiceImplTest {

    @Mock private AlipayClient alipayClient;
    @Mock private AlipayConfig alipayConfig;
    @Mock private TWalletRechargeOrderMapper orderMapper;
    @Mock private IWalletService walletService;
    @Mock private IOrderService orderService;
    @Mock private ObjectMapper objectMapper;
    @Mock private TransactionTemplate alipayTransactionTemplate;

    @InjectMocks
    private AlipayServiceImpl alipayService;

    // ============ createRechargeOrder ============

    @Test
    @DisplayName("createRechargeOrder 成功 → 落 PENDING 充值单 + 返回 payForm")
    void createRechargeOrder_success() throws Exception {
        when(alipayConfig.getNotifyUrl()).thenReturn("http://localhost/notify");
        when(alipayConfig.getReturnUrl()).thenReturn("http://localhost/return");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        AlipayTradePagePayResponse resp = new AlipayTradePagePayResponse();
        resp.setBody("<form>pay</form>");
        when(alipayClient.pageExecute(any())).thenReturn(resp);
        when(orderMapper.insert(any(TWalletRechargeOrder.class))).thenAnswer(inv -> {
            TWalletRechargeOrder o = inv.getArgument(0);
            o.setId(99L);
            return 1;
        });

        RechargeDTOs.RechargeOrderVO vo = alipayService.createRechargeOrder(1L, new BigDecimal("100"));

        assertThat(vo.getOutTradeNo()).startsWith("rc1");
        assertThat(vo.getPayForm()).contains("<form>pay</form>");
    }

    @Test
    @DisplayName("createRechargeOrder userId=null → UNAUTHORIZED")
    void createRechargeOrder_unauthorized() {
        assertThatThrownBy(() -> alipayService.createRechargeOrder(null, new BigDecimal("100")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("createRechargeOrder 金额 0/负 → BAD_REQUEST")
    void createRechargeOrder_nonPositive() {
        assertThatThrownBy(() -> alipayService.createRechargeOrder(1L, BigDecimal.ZERO))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("createRechargeOrder 金额 > 100000 → BAD_REQUEST")
    void createRechargeOrder_exceedMax() {
        assertThatThrownBy(() -> alipayService.createRechargeOrder(1L, new BigDecimal("999999")))
                .isInstanceOf(BizException.class);
    }

    // ============ queryOrder ============

    @Test
    @DisplayName("queryOrder 已 PAID → 返回 PAID + balance")
    void queryOrder_paid() {
        TWalletRechargeOrder o = new TWalletRechargeOrder();
        o.setOutTradeNo("OTN-001");
        o.setUserId(1L);
        o.setStatus("PAID");
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(o);

        com.melody.entity.TWalletAccount wallet = new com.melody.entity.TWalletAccount();
        wallet.setBalance(new BigDecimal("200"));
        when(walletService.getAccount(1L)).thenReturn(wallet);

        RechargeDTOs.RechargeQueryVO vo = alipayService.queryOrder("OTN-001", 1L);
        assertThat(vo.getStatus()).isEqualTo("PAID");
        assertThat(vo.getBalance()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("queryOrder 订单不存在 → NOT_FOUND")
    void queryOrder_missing() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> alipayService.queryOrder("OTN-X", 1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("queryOrder 非本人订单 → FORBIDDEN")
    void queryOrder_notOwned() {
        TWalletRechargeOrder o = new TWalletRechargeOrder();
        o.setUserId(2L);
        o.setStatus("PENDING");
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(o);
        assertThatThrownBy(() -> alipayService.queryOrder("OTN-X", 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("queryOrder 空 outTradeNo → BAD_REQUEST")
    void queryOrder_emptyOutTradeNo() {
        assertThatThrownBy(() -> alipayService.queryOrder("", 1L))
                .isInstanceOf(BizException.class);
    }

    // ============ handleNotify ============

    @Test
    @DisplayName("handleNotify 空参数 → fail")
    void handleNotify_empty() {
        assertThat(alipayService.handleNotify(null)).isEqualTo("fail");
        assertThat(alipayService.handleNotify(java.util.Collections.emptyMap())).isEqualTo("fail");
    }

    // ============ createOrderPayForm ============

    @Test
    @DisplayName("createOrderPayForm 非 PENDING_PAY → ORDER_STATUS_INVALID")
    void createOrderPayForm_wrongStatus() {
        TOrder order = new TOrder();
        order.setId(1L);
        order.setUserId(1L);
        order.setOrderNo("ORD-001");
        order.setStatus("COMPLETED");
        order.setTotalPrice(new BigDecimal("100"));
        when(orderService.getById(1L)).thenReturn(order);

        assertThatThrownBy(() -> alipayService.createOrderPayForm(1L, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.ORDER_STATUS_INVALID.getCode()));
    }

    @Test
    @DisplayName("createOrderPayForm 成功")
    void createOrderPayForm_success() throws Exception {
        TOrder order = new TOrder();
        order.setId(1L);
        order.setUserId(1L);
        order.setOrderNo("ORD-001");
        order.setStatus("PENDING_PAY");
        order.setTotalPrice(new BigDecimal("100"));
        when(orderService.getById(1L)).thenReturn(order);
        when(alipayConfig.getOrderNotifyUrl()).thenReturn("http://localhost/n");
        when(alipayConfig.getOrderReturnUrl()).thenReturn("http://localhost/order/{id}");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        AlipayTradePagePayResponse resp = new AlipayTradePagePayResponse();
        resp.setBody("<form>order-pay</form>");
        when(alipayClient.pageExecute(any())).thenReturn(resp);

        RechargeDTOs.OrderPayFormVO vo = alipayService.createOrderPayForm(1L, 1L);
        assertThat(vo.getOrderNo()).isEqualTo("ORD-001");
        assertThat(vo.getPayForm()).contains("order-pay");
    }

    // ============ queryOrderPay ============

    @Test
    @DisplayName("queryOrderPay 已 PENDING_SHIP → PAID + paidAt")
    void queryOrderPay_paidStatus() {
        TOrder order = new TOrder();
        order.setId(1L);
        order.setUserId(1L);
        order.setOrderNo("ORD-001");
        order.setStatus("PENDING_SHIP");
        order.setPaidAt(java.time.LocalDateTime.of(2024, 1, 1, 12, 0));
        when(orderService.getById(1L)).thenReturn(order);

        RechargeDTOs.OrderPayQueryVO vo = alipayService.queryOrderPay(1L, 1L);
        assertThat(vo.getStatus()).isEqualTo("PAID");
        assertThat(vo.getPaidAt()).isNotEmpty();
    }

    @Test
    @DisplayName("queryOrderPay CANCELED → PENDING（未付）")
    void queryOrderPay_canceled() {
        TOrder order = new TOrder();
        order.setId(1L);
        order.setUserId(1L);
        order.setOrderNo("ORD-001");
        order.setStatus("CANCELED");
        when(orderService.getById(1L)).thenReturn(order);

        RechargeDTOs.OrderPayQueryVO vo = alipayService.queryOrderPay(1L, 1L);
        assertThat(vo.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("queryOrderPay 不存在 → ORDER_NOT_FOUND")
    void queryOrderPay_missing() {
        when(orderService.getById(99L))
                .thenThrow(new BizException(ResultCode.ORDER_NOT_FOUND));
        assertThatThrownBy(() -> alipayService.queryOrderPay(1L, 99L))
                .isInstanceOf(BizException.class);
    }
}
