package com.melody.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.entity.TProduct;
import com.melody.entity.TProductSku;
import com.melody.service.IProductService;
import com.melody.service.impl.OperationLogServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminProductController 测试 — 使用 MockMvc standaloneSetup。
 *
 * <p>由于 AdminProductController 用了 @Value 注入 imageRoot，
 * standaloneSetup 不走 Spring 容器，用 ReflectionTestUtils 手动注入。</p>
 */
@DisplayName("AdminProductController 测试")
class AdminProductControllerTest {

    private MockMvc mockMvc;
    private IProductService productService;
    private OperationLogServiceImpl opLog;
    private MockedStatic<SecurityUtils> mockedSecurity;

    @BeforeEach
    void setUp() {
        productService = mock(IProductService.class);
        opLog = mock(OperationLogServiceImpl.class);
        AdminProductController controller = new AdminProductController(productService, opLog);
        // standaloneSetup 不处理 @Value 注入，手动 set
        ReflectionTestUtils.setField(controller, "imageRoot", "/tmp/__test_image_root__");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mockedSecurity = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurity.when(SecurityUtils::currentId).thenReturn(99L);
        mockedSecurity.when(SecurityUtils::currentUsername).thenReturn("admin");
    }

    @AfterEach
    void tearDown() {
        mockedSecurity.close();
    }

    private TProduct newProduct(Long id, String name) {
        TProduct p = new TProduct();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal("9.90"));
        p.setStatus(1);
        return p;
    }

    @Test
    @DisplayName("GET /api/admin/product/list 需 product:read 权限")
    void list_ok() throws Exception {
        when(productService.list(any(), any(), eq("default"))).thenReturn(List.of(newProduct(1L, "可乐")));

        mockMvc.perform(get("/api/admin/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("可乐"));
    }

    @Test
    @DisplayName("POST /api/admin/product/create 创建商品")
    void create_ok() throws Exception {
        TProduct saved = newProduct(10L, "新商品");
        when(productService.create(any(), any())).thenReturn(saved);

        AdminProductController.ProductCreateRequest req = new AdminProductController.ProductCreateRequest();
        req.setProduct(saved);
        req.setSkus(List.of());

        mockMvc.perform(post("/api/admin/product/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));

        verify(opLog).log(eq("product"), any());
    }

    @Test
    @DisplayName("PUT /api/admin/product/{id}")
    void update_ok() throws Exception {
        TProduct saved = newProduct(1L, "改名");
        when(productService.update(eq(1L), any())).thenReturn(saved);

        AdminProductController.ProductUpdateRequest req = new AdminProductController.ProductUpdateRequest();
        req.setProduct(saved);

        mockMvc.perform(put("/api/admin/product/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名"));
    }

    @Test
    @DisplayName("DELETE /api/admin/product/{id} 下架")
    void delete_ok() throws Exception {
        mockMvc.perform(delete("/api/admin/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("下架成功"));

        verify(productService).delete(1L);
    }

    @Test
    @DisplayName("GET /api/admin/sku?productId=1")
    void skuList_ok() throws Exception {
        TProductSku sku = new TProductSku();
        sku.setId(1L);
        when(productService.listSkus(1L)).thenReturn(List.of(sku));

        mockMvc.perform(get("/api/admin/sku").param("productId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("GET /api/admin/category/list")
    void categories_ok() throws Exception {
        when(productService.listCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/category/list"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/admin/category/create")
    void createCategory_ok() throws Exception {
        com.melody.entity.TCategory c = new com.melody.entity.TCategory();
        c.setId(99L);
        c.setName("饮料");
        when(productService.createCategory(any())).thenReturn(c);

        mockMvc.perform(post("/api/admin/category/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(c)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99));
    }

    @Test
    @DisplayName("DELETE /api/admin/category/{id}")
    void deleteCategory_ok() throws Exception {
        mockMvc.perform(delete("/api/admin/category/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("已停用"));

        verify(productService).deleteCategory(1L);
    }

    @Test
    @DisplayName("GET /api/admin/product/image/list → 当 imageRoot 不存在时返回空数组")
    void imageList_empty() throws Exception {
        mockMvc.perform(get("/api/admin/product/image/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }
}
