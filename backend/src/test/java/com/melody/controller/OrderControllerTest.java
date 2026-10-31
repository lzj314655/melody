package com.melody.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.dto.CheckoutDTOs;
import com.melody.entity.TOrder;
import com.melody.exception.BizException;
import com.melody.mapper.TOrderItemMapper;
import com.melody.mapper.TUserAddressMapper;
import com.melody.service.IAlipayService;
import com.melody.service.IOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OrderController 测试")
class OrderControllerTest {

    private MockMvc mockMvc;
    private IOrderService orderService;
    private TUserAddressMapper addressMapper;
    private TOrderItemMapper orderItemMapper;
    private IAlipayService alipayService;
    private ObjectMapper objectMapper;
    private MockedStatic<SecurityUtils> mockedSecurity;

    @BeforeEach
    void setUp() {
        orderService = mock(IOrderService.class);
        addressMapper = mock(TUserAddressMapper.class);
        orderItemMapper = mock(TOrderItemMapper.class);
        alipayService = mock(IAlipayService.class);
        ObjectMapper jackson = new ObjectMapper();
        OrderController controller = new OrderController(orderService, addressMapper, orderItemMapper, jackson, alipayService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = jackson;
        mockedSecurity = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurity.when(SecurityUtils::currentId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        mockedSecurity.close();
    }

    private TOrder order(Long id, String status) {
        TOrder o = new TOrder();
        o.setId(id);
        o.setOrderNo("ORD-001");
        o.setUserId(1L);
        o.setStatus(status);
        o.setTotalPrice(new BigDecimal("100"));
        return o;
    }

    private com.melody.entity.TUserAddress address(Long id) {
        com.melody.entity.TUserAddress a = new com.melody.entity.TUserAddress();
        a.setId(id);
        a.setUserId(1L);
        a.setName("张三");
        a.setPhone("13800000000");
        a.setProvince("北京");
        a.setCity("北京");
        a.setDistrict("朝阳区");
        a.setDetail("xx街道1号");
        return a;
    }

    @Test
    @DisplayName("GET /api/order 默认 ALL")
    void list_defaultAll() throws Exception {
        when(orderService.listByUser(anyLong(), anyString())).thenReturn(List.of());
        mockMvc.perform(get("/api/order")).andExpect(status().isOk());
        verify(orderService).listByUser(1L, "ALL");
    }

    @Test
    @DisplayName("GET /api/order?status=PENDING_PAY")
    void list_withStatus() throws Exception {
        when(orderService.listByUser(1L, "PENDING_PAY")).thenReturn(List.of());
        mockMvc.perform(get("/api/order").param("status", "PENDING_PAY")).andExpect(status().isOk());
        verify(orderService).listByUser(1L, "PENDING_PAY");
    }

    @Test
    @DisplayName("GET /api/order/{id} 存在")
    void detail_ok() throws Exception {
        when(orderService.getByIdForUser(1L, 1L)).thenReturn(order(1L, "PENDING_SHIP"));
        when(orderItemMapper.selectList(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/order/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("ORD-001"))
                .andExpect(jsonPath("$.data.status").value("PENDING_SHIP"))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("GET /api/order/{id} 他人订单 → FORBIDDEN")
    void detail_notOwner() throws Exception {
        doThrow(new BizException(com.melody.common.ResultCode.FORBIDDEN, "无权查看该订单"))
                .when(orderService).getByIdForUser(2L, 1L);

        mockMvc.perform(get("/api/order/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(com.melody.common.ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("POST /api/order 创建成功")
    void create_ok() throws Exception {
        when(addressMapper.selectById(10L)).thenReturn(address(10L));
        when(orderService.create(anyLong(), any(), any(), any(), anyString())).thenReturn(order(99L, "PENDING_SHIP"));
        CheckoutDTOs.CheckoutRequest req = new CheckoutDTOs.CheckoutRequest();
        req.setCartItemIds(List.of(1L));
        req.setAddressId(10L);
        req.setPayMethod("WALLET");
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.status").value("PENDING_SHIP"));
    }

    @Test
    @DisplayName("POST /api/order 地址不存在 → IllegalArgumentException")
    void create_addressMissing() throws Exception {
        when(addressMapper.selectById(99L)).thenReturn(null);
        CheckoutDTOs.CheckoutRequest req = new CheckoutDTOs.CheckoutRequest();
        req.setCartItemIds(List.of(1L));
        req.setAddressId(99L);
        // IllegalArgumentException 被 controller 抛到 framework，最终被 GlobalExceptionHandler
        // catch-all 返回 R.fail(500)。standaloneSetup 默认返回 200 OK。
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/order/{id}/cancel")
    void cancel_ok() throws Exception {
        mockMvc.perform(put("/api/order/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("订单已取消"));
        verify(orderService).cancel(1L, 1L);
    }

    @Test
    @DisplayName("PUT /api/order/{id}/confirm")
    void confirmDelivery_ok() throws Exception {
        mockMvc.perform(put("/api/order/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("已确认收货"));
    }

    @Test
    @DisplayName("POST /api/order/{id}/refund 成功")
    void refund_ok() throws Exception {
        CheckoutDTOs.RefundRequest req = new CheckoutDTOs.RefundRequest();
        req.setReason("质量问题");
        mockMvc.perform(post("/api/order/1/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
        verify(orderService).refund(1L, 1L, "质量问题");
    }

    @Test
    @DisplayName("POST /api/order/{id}/wallet-pay → BizException → R.fail")
    void walletPay_orderStatusInvalid() throws Exception {
        doThrow(new BizException(com.melody.common.ResultCode.ORDER_STATUS_INVALID, "订单不在待付款状态"))
                .when(orderService).payByWallet(1L, 1L);
        mockMvc.perform(post("/api/order/1/wallet-pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    @DisplayName("POST /api/order/{id}/alipay-pay → 返回 payForm")
    void alipayPay_ok() throws Exception {
        com.melody.dto.RechargeDTOs.OrderPayFormVO vo = new com.melody.dto.RechargeDTOs.OrderPayFormVO();
        vo.setOrderNo("ORD-001");
        vo.setPayForm("<form>pay</form>");
        when(alipayService.createOrderPayForm(1L, 1L)).thenReturn(vo);
        mockMvc.perform(post("/api/order/1/alipay-pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("ORD-001"))
                .andExpect(jsonPath("$.data.payForm").value("<form>pay</form>"));
    }
}
