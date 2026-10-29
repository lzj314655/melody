package com.melody.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.dto.ReviewDTOs;
import com.melody.service.IReviewService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ReviewController 测试")
class ReviewControllerTest {

    private MockMvc mockMvc;
    private IReviewService reviewService;
    private MockedStatic<SecurityUtils> mockedSecurity;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reviewService = mock(IReviewService.class);
        ReviewController controller = new ReviewController(reviewService);
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
    @DisplayName("GET /api/review/product/{productId}")
    void listByProduct_ok() throws Exception {
        ReviewDTOs.ReviewPageVO vo = new ReviewDTOs.ReviewPageVO();
        vo.setTotal(0L);
        when(reviewService.listReviews(anyLong(), anyInt(), anyInt())).thenReturn(vo);

        mockMvc.perform(get("/api/review/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("GET /api/review/eligibility?productId=1")
    void eligibility_ok() throws Exception {
        ReviewDTOs.ReviewEligibilityVO vo = new ReviewDTOs.ReviewEligibilityVO();
        vo.setCanReview(true);
        when(reviewService.checkEligibility(anyLong(), anyLong())).thenReturn(vo);

        mockMvc.perform(get("/api/review/eligibility").param("productId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canReview").value(true));
    }

    @Test
    @DisplayName("POST /api/review 提交评价成功")
    void submit_ok() throws Exception {
        when(reviewService.submitReview(anyLong(), any())).thenReturn(123L);

        ReviewDTOs.ReviewSubmitRequest req = new ReviewDTOs.ReviewSubmitRequest();
        req.setProductId(1L);
        req.setRating(5);
        req.setContent("好吃");

        mockMvc.perform(post("/api/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(123));
    }

    @Test
    @DisplayName("POST /api/review 未登录 → R.fail 1002")
    void submit_unauthorized() throws Exception {
        mockedSecurity.close();
        mockedSecurity = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurity.when(SecurityUtils::currentId).thenReturn(null);

        ReviewDTOs.ReviewSubmitRequest req = new ReviewDTOs.ReviewSubmitRequest();
        req.setProductId(1L);
        req.setRating(5);
        req.setContent("好吃");

        mockMvc.perform(post("/api/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }
}
