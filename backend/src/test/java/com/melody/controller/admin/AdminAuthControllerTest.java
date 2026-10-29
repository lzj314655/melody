package com.melody.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.SecurityUtils;
import com.melody.config.GlobalExceptionHandler;
import com.melody.dto.AdminDTOs;
import com.melody.entity.TAdmin;
import com.melody.exception.BizException;
import com.melody.service.IAdminService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminAuthController 测试")
class AdminAuthControllerTest {

    private MockMvc mockMvc;
    private IAdminService adminService;
    private MockedStatic<SecurityUtils> mockedSecurity;

    @BeforeEach
    void setUp() {
        adminService = mock(IAdminService.class);
        AdminAuthController controller = new AdminAuthController(adminService);
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

    private AdminDTOs.AdminLoginRequest loginReq() {
        AdminDTOs.AdminLoginRequest r = new AdminDTOs.AdminLoginRequest();
        r.setUsername("root");
        r.setPassword("123456");
        return r;
    }

    @Test
    @DisplayName("POST /api/admin/auth/login 成功 → token")
    void login_success() throws Exception {
        TAdmin a = new TAdmin();
        a.setId(1L);
        a.setUsername("root");
        when(adminService.login("root", "123456")).thenReturn("mock.admin.token");
        when(adminService.list()).thenReturn(List.of(a));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(loginReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("mock.admin.token"))
                .andExpect(jsonPath("$.data.admin.username").value("root"));
    }

    @Test
    @DisplayName("POST /api/admin/auth/login 密码错 → R.fail 2002")
    void login_wrongPassword() throws Exception {
        when(adminService.login(anyString(), anyString()))
                .thenThrow(new BizException(com.melody.common.ResultCode.USER_PASSWORD_ERROR));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(loginReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("POST /api/admin/auth/login 缺 password → 业务校验失败")
    void login_missingPassword_400() throws Exception {
        AdminDTOs.AdminLoginRequest r = new AdminDTOs.AdminLoginRequest();
        r.setUsername("root");
        // 不传 password，service 收到 null → 调用 service.login 抛 BizException
        when(adminService.login(anyString(), any()))
                .thenThrow(new BizException(com.melody.common.ResultCode.BAD_REQUEST, "密码不能为空"));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("GET /api/admin/auth/me 当前管理员")
    void me_ok() throws Exception {
        TAdmin a = new TAdmin();
        a.setId(1L);
        a.setUsername("root");
        when(adminService.getById(1L)).thenReturn(a);

        mockMvc.perform(get("/api/admin/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("root"));
    }
}
