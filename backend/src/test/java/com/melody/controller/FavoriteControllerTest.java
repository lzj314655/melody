package com.melody.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.mapper.TFavoriteMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("FavoriteController 测试")
class FavoriteControllerTest {

    private MockMvc mockMvc;
    private TFavoriteMapper favoriteMapper;
    private MockedStatic<SecurityUtils> mockedSecurity;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        favoriteMapper = mock(TFavoriteMapper.class);
        FavoriteController controller = new FavoriteController(favoriteMapper);
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

    @Test
    @DisplayName("GET /api/favorite 当前用户 → 返回 productId 列表")
    void list_ok() throws Exception {
        com.melody.entity.TFavorite f = new com.melody.entity.TFavorite();
        f.setProductId(100L);
        when(favoriteMapper.selectList(any())).thenReturn(List.of(f));

        mockMvc.perform(get("/api/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(100));
    }

    @Test
    @DisplayName("GET /api/favorite 未登录 → 空列表")
    void list_unauthorized() throws Exception {
        mockedSecurity.close();
        mockedSecurity = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurity.when(SecurityUtils::currentId).thenReturn(null);

        mockMvc.perform(get("/api/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("POST /api/favorite/{productId}")
    void add_ok() throws Exception {
        mockMvc.perform(post("/api/favorite/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("已收藏"));

        verify(favoriteMapper).insert(any());
    }

    @Test
    @DisplayName("DELETE /api/favorite/{productId}")
    void remove_ok() throws Exception {
        mockMvc.perform(delete("/api/favorite/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("已取消"));

        verify(favoriteMapper).delete(any());
    }

    @Test
    @DisplayName("POST /api/favorite/check")
    void check_ok() throws Exception {
        com.melody.entity.TFavorite f = new com.melody.entity.TFavorite();
        f.setProductId(100L);
        when(favoriteMapper.selectList(any())).thenReturn(List.of(f));

        mockMvc.perform(post("/api/favorite/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productIds", List.of(100L, 200L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ids[0]").value(100));
    }
}
