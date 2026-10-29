package com.melody.controller;

import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.entity.TCoupon;
import com.melody.entity.TUserCoupon;
import com.melody.service.ICouponService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CouponController 测试")
class CouponControllerTest {

    private MockMvc mockMvc;
    private ICouponService couponService;
    private MockedStatic<SecurityUtils> mockedSecurity;

    @BeforeEach
    void setUp() {
        couponService = mock(ICouponService.class);
        CouponController controller = new CouponController(couponService);
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
    @DisplayName("GET /api/coupon/available")
    void available_ok() throws Exception {
        TCoupon c = new TCoupon();
        c.setId(1L);
        when(couponService.listAvailable()).thenReturn(java.util.List.of(c));

        mockMvc.perform(get("/api/coupon/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("POST /api/coupon/{couponId}/claim")
    void claim_ok() throws Exception {
        TUserCoupon uc = new TUserCoupon();
        uc.setId(99L);
        when(couponService.claim(1L, 5L)).thenReturn(uc);

        mockMvc.perform(post("/api/coupon/5/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99));

        verify(couponService).claim(1L, 5L);
    }

    @Test
    @DisplayName("GET /api/coupon/mine?status=可用")
    void mine_withStatus() throws Exception {
        when(couponService.myCoupons(1L, "可用")).thenReturn(java.util.Collections.emptyList());
        mockMvc.perform(get("/api/coupon/mine").param("status", "可用"))
                .andExpect(status().isOk());
        verify(couponService).myCoupons(1L, "可用");
    }

    @Test
    @DisplayName("GET /api/coupon/mine 默认 status=可用")
    void mine_defaultStatus() throws Exception {
        when(couponService.myCoupons(anyLong(), anyString())).thenReturn(java.util.Collections.emptyList());
        mockMvc.perform(get("/api/coupon/mine"))
                .andExpect(status().isOk());
        verify(couponService).myCoupons(1L, "可用");
    }
}
