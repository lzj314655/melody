package com.melody.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.dto.CheckoutDTOs;
import com.melody.entity.TCartItem;
import com.melody.service.ICartService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CartController 测试")
class CartControllerTest {

    private MockMvc mockMvc;
    private ICartService cartService;
    private MockedStatic<SecurityUtils> mockedSecurity;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cartService = mock(ICartService.class);
        CartController controller = new CartController(cartService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        mockedSecurity = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurity.when(SecurityUtils::currentId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        mockedSecurity.close();
    }

    private CheckoutDTOs.CartAddRequest addReq() {
        CheckoutDTOs.CartAddRequest r = new CheckoutDTOs.CartAddRequest();
        r.setProductId(100L);
        r.setSkuId(10L);
        r.setQuantity(2);
        return r;
    }

    @Test
    @DisplayName("GET /api/cart 当前用户")
    void list_ok() throws Exception {
        when(cartService.list(1L)).thenReturn(java.util.Collections.emptyList());

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/cart 加购成功 → 200")
    void add_ok() throws Exception {
        TCartItem item = new TCartItem();
        item.setId(1L);
        when(cartService.add(anyLong(), anyLong(), anyLong(), any())).thenReturn(item);

        mockMvc.perform(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("PUT /api/cart/{productId}/{skuId}?quantity=5")
    void updateQty_ok() throws Exception {
        mockMvc.perform(put("/api/cart/100/10").param("quantity", "5"))
                .andExpect(status().isOk());

        verify(cartService).updateQuantity(1L, 100L, 10L, 5);
    }

    @Test
    @DisplayName("DELETE /api/cart/{productId}/{skuId}")
    void remove_ok() throws Exception {
        mockMvc.perform(delete("/api/cart/100/10"))
                .andExpect(status().isOk());

        verify(cartService).remove(1L, 100L, 10L);
    }

    @Test
    @DisplayName("PUT /api/cart/{productId}/{skuId}/select  body 传 selected")
    void toggleSelect_withBody() throws Exception {
        mockMvc.perform(put("/api/cart/100/10/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selected\":true}"))
                .andExpect(status().isOk());

        verify(cartService).setSelect(1L, 100L, 10L, true);
    }

    @Test
    @DisplayName("PUT /api/cart/select-all?selectAll=true")
    void toggleSelectAll_param() throws Exception {
        mockMvc.perform(put("/api/cart/select-all").param("selectAll", "true"))
                .andExpect(status().isOk());

        verify(cartService).toggleSelectAll(1L, true);
        verify(cartService, times(0)).setSelect(anyLong(), anyLong(), anyLong(), anyBoolean());
    }
}
