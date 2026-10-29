package com.melody.controller;

import com.melody.common.ResultCode;
import com.melody.config.GlobalExceptionHandler;
import com.melody.entity.TCategory;
import com.melody.entity.TProduct;
import com.melody.exception.BizException;
import com.melody.service.IProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ProductController 测试")
class ProductControllerTest {

    private MockMvc mockMvc;
    private IProductService productService;

    @BeforeEach
    void setUp() {
        productService = mock(IProductService.class);
        ProductController controller = new ProductController(productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private TProduct newProduct(Long id) {
        TProduct p = new TProduct();
        p.setId(id);
        p.setName("可乐");
        p.setPrice(new BigDecimal("3.50"));
        return p;
    }

    @Test
    @DisplayName("GET /api/product/categories 公开 200")
    void categories_ok() throws Exception {
        when(productService.listCategories()).thenReturn(List.of(new TCategory()));

        mockMvc.perform(get("/api/product/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/product/list 默认排序")
    void list_default() throws Exception {
        when(productService.list(any(), any(), any())).thenReturn(List.of(newProduct(1L)));

        mockMvc.perform(get("/api/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("GET /api/product/list?categoryId=1&keyword=可乐&sort=price-asc")
    void list_withParams() throws Exception {
        when(productService.list(eq(1L), eq("可乐"), eq("price-asc"))).thenReturn(List.of());

        mockMvc.perform(get("/api/product/list")
                        .param("categoryId", "1")
                        .param("keyword", "可乐")
                        .param("sort", "price-asc"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/product/detail/{id} 存在 → 200")
    void detail_ok() throws Exception {
        when(productService.getDetail(1L)).thenReturn(newProduct(1L));

        mockMvc.perform(get("/api/product/detail/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("可乐"));
    }

    @Test
    @DisplayName("GET /api/product/detail/{id} 不存在 → R.fail 3001")
    void detail_missing() throws Exception {
        when(productService.getDetail(99L)).thenThrow(new BizException(ResultCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/product/detail/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    @DisplayName("GET /api/sku?productId=1")
    void skus_ok() throws Exception {
        when(productService.listSkus(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/sku").param("productId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
