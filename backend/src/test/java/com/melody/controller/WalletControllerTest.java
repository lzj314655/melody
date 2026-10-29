package com.melody.controller;

import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.entity.TWalletAccount;
import com.melody.exception.BizException;
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
    private MockedStatic<SecurityUtils> mockedSecurity;

    @BeforeEach
    void setUp() {
        walletService = mock(IWalletService.class);
        WalletController controller = new WalletController(walletService);
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
    @DisplayName("POST /api/wallet/recharge?amount=100 成功")
    void recharge_ok() throws Exception {
        TWalletAccount a = new TWalletAccount();
        a.setBalance(new BigDecimal("100"));
        when(walletService.getAccount(1L)).thenReturn(a);

        mockMvc.perform(post("/api/wallet/recharge").param("amount", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(100));

        verify(walletService).recharge(anyLong(), any(), any());
    }

    @Test
    @DisplayName("POST /api/wallet/recharge?amount=0 → BAD_REQUEST")
    void recharge_zeroAmount() throws Exception {
        mockMvc.perform(post("/api/wallet/recharge").param("amount", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("POST /api/wallet/recharge 钱包不存在 → NOT_FOUND")
    void recharge_accountMissing() throws Exception {
        doThrow(new BizException(com.melody.common.ResultCode.NOT_FOUND, "钱包账户不存在"))
                .when(walletService).recharge(anyLong(), any(), any());

        mockMvc.perform(post("/api/wallet/recharge").param("amount", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));
    }
}
