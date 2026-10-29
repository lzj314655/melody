package com.melody.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.dto.AdminDTOs;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.UserBehaviorLogMapper;
import com.melody.service.IBehaviorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("BehaviorController 测试")
class BehaviorControllerTest {

    private MockMvc mockMvc;
    private IBehaviorService behaviorService;
    private UserBehaviorLogMapper logMapper;
    private TProductMapper productMapper;
    private MockedStatic<SecurityUtils> mockedSecurity;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        behaviorService = mock(IBehaviorService.class);
        logMapper = mock(UserBehaviorLogMapper.class);
        productMapper = mock(TProductMapper.class);
        BehaviorController controller = new BehaviorController(behaviorService, new ObjectMapper(), logMapper, productMapper);
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
    @DisplayName("POST /api/behavior/report VIEW")
    void report_view() throws Exception {
        AdminDTOs.BehaviorReportRequest req = new AdminDTOs.BehaviorReportRequest();
        req.setBehaviorType("VIEW");
        req.setProductId(100L);

        mockMvc.perform(post("/api/behavior/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(behaviorService).report(anyLong(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("POST /api/behavior/report 非法类型 → R.fail")
    void report_invalidType() throws Exception {
        AdminDTOs.BehaviorReportRequest req = new AdminDTOs.BehaviorReportRequest();
        req.setBehaviorType("UNKNOWN_TYPE");
        req.setProductId(100L);

        mockMvc.perform(post("/api/behavior/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
}
