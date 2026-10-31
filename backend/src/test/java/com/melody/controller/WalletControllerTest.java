package com.melody.controller;

import com.melody.common.ResultCode;
import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.dto.RechargeDTOs;
import com.melody.entity.TWalletAccount;
import com.melody.exception.BizException;
import com.melody.service.IAlipayService;
import com.melody.service.IWalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("WalletController 测试")
class WalletControllerTest {

    private MockMvc mockMvc;
    private IWalletService walletService;
    private IAlipayService alipayService;
    private MockedStatic<SecurityUtils> mockedSecurity;

    @BeforeEach
    void setUp() {
        walletService = mock(IWalletService.class);
        alipayService = mock(IAlipayService.class);
        WalletController controller = new WalletController(walletService, alipayService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mockedSecurity = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurity.when(SecurityUtils::currentId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        mockedSecurity.close();
    }

    @Test
    @DisplayName("GET /api/wallet 当前余额")
    void get_ok() throws Exception {
        TWalletAccount a = new TWalletAccount();
        a.setBalance(new BigDecimal("200"));
        when(walletService.getAccount(1L)).thenReturn(a);

        mockMvc.perform(get("/api/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(200));
    }

    @Test
    @DisplayName("GET /api/wallet/logs")
    void logs_ok() throws Exception {
        when(walletService.listLogs(1L)).thenReturn(java.util.Collections.emptyList());

        mockMvc.perform(get("/api/wallet/logs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/wallet/recharge 创建支付宝充值单（不再直接加余额）")
    void recharge_createsAlipayOrder() throws Exception {
        RechargeDTOs.RechargeOrderVO vo = new RechargeDTOs.RechargeOrderVO();
        vo.setOutTradeNo("rc123");
        vo.setPayForm("<form>pay</form>");
        when(alipayService.createRechargeOrder(1L, new BigDecimal("100"))).thenReturn(vo);

        mockMvc.perform(post("/api/wallet/recharge").param("amount", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outTradeNo").value("rc123"))
                .andExpect(jsonPath("$.data.payForm").value("<form>pay</form>"));

        verify(walletService, Mockito.never()).recharge(anyLong(), any(), any());
    }

    @Test
    @DisplayName("POST /api/wallet/recharge?amount=0 → BAD_REQUEST")
    void recharge_zeroAmount() throws Exception {
        doThrow(new BizException(ResultCode.BAD_REQUEST, "充值金额必须大于 0"))
                .when(alipayService).createRechargeOrder(eq(1L), eq(BigDecimal.ZERO));

        mockMvc.perform(post("/api/wallet/recharge").param("amount", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("GET /api/wallet/recharge/query 返回充值状态")
    void rechargeQuery_ok() throws Exception {
        RechargeDTOs.RechargeQueryVO vo = new RechargeDTOs.RechargeQueryVO();
        vo.setOutTradeNo("rc123");
        vo.setStatus("PAID");
        vo.setBalance(new BigDecimal("300"));
        when(alipayService.queryOrder("rc123", 1L)).thenReturn(vo);

        mockMvc.perform(get("/api/wallet/recharge/query").param("outTradeNo", "rc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.balance").value(300));
    }
}
