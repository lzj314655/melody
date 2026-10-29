package com.melody.controller;

import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.entity.TProduct;
import com.melody.service.IProductService;
import com.melody.service.IRecommendService;
import com.melody.task.RecommendTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("RecommendController 测试")
class RecommendControllerTest {

    private MockMvc mockMvc;
    private IRecommendService recommendService;
    private IProductService productService;
    private RecommendTask recommendTask;
    private MockedStatic<SecurityUtils> mockedSecurity;

    @BeforeEach
    void setUp() {
        recommendService = mock(IRecommendService.class);
        productService = mock(IProductService.class);
        recommendTask = mock(RecommendTask.class);
        RecommendController controller = new RecommendController(recommendService, productService, recommendTask);
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

    private TProduct newProduct(Long id) {
        TProduct p = new TProduct();
        p.setId(id);
        p.setName("p" + id);
        p.setPrice(new BigDecimal("9.90"));
        p.setStatus(1);
        return p;
    }

    @Test
    @DisplayName("GET /api/recommend/personal?limit=5")
    void personal_ok() throws Exception {
        when(recommendService.personalRecommend(anyLong(), anyInt())).thenReturn(List.of(1L, 2L));
        when(productService.list(null, null, "default")).thenReturn(List.of(newProduct(1L), newProduct(2L)));

        mockMvc.perform(get("/api/recommend/personal").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2));
    }

    @Test
    @DisplayName("GET /api/recommend/similar/{productId}")
    void similar_ok() throws Exception {
        when(recommendService.similarRecommend(100L, 6)).thenReturn(List.of(200L, 300L));
        when(productService.list(null, null, "default"))
                .thenReturn(List.of(newProduct(200L), newProduct(300L)));

        mockMvc.perform(get("/api/recommend/similar/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(200));
    }

    @Test
    @DisplayName("GET /api/recommend/cold-start?limit=20")
    void coldStart_ok() throws Exception {
        when(recommendService.coldStart(20)).thenReturn(List.of(10L));
        when(productService.list(null, null, "default")).thenReturn(List.of(newProduct(10L)));

        mockMvc.perform(get("/api/recommend/cold-start").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10));
    }

    @Test
    @DisplayName("GET /api/recommend/personal 匿名 → coldStart 自动兜底")
    void personal_anonymous() throws Exception {
        mockedSecurity.close();
        mockedSecurity = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurity.when(SecurityUtils::currentId).thenReturn(null);

        when(recommendService.personalRecommend(null, 10)).thenReturn(List.of(1L));
        when(productService.list(null, null, "default")).thenReturn(List.of(newProduct(1L)));

        mockMvc.perform(get("/api/recommend/personal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }
}
