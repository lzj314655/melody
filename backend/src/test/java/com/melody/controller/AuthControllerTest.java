package com.melody.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.JwtUtil;
import com.melody.common.ResultCode;
import com.melody.config.GlobalExceptionHandler;
import com.melody.controller.auth.AuthController;
import com.melody.dto.AuthDTOs;
import com.melody.entity.TUser;
import com.melody.exception.BizException;
import com.melody.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 测试 — 使用 MockMvc standaloneSetup 避免 @MapperScan 冲突。
 */
@DisplayName("AuthController 测试")
class AuthControllerTest {

    private MockMvc mockMvc;
    private IUserService userService;
    private JwtUtil jwtUtil;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userService = mock(IUserService.class);
        jwtUtil = mock(JwtUtil.class);
        AuthController controller = new AuthController(userService, jwtUtil);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        // 加入 ResponseEntityExceptionHandler 让 @Valid 失败时返回 400
        org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler defaultHandler =
                new org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler() {};
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), defaultHandler)
                .setValidator(validator)
                .build();
        objectMapper = new ObjectMapper();
    }

    private AuthDTOs.LoginRequest loginReq() {
        AuthDTOs.LoginRequest r = new AuthDTOs.LoginRequest();
        r.setPhone("13800000001");
        r.setPassword("123456");
        r.setCaptchaId("cap-1");
        r.setCaptchaCode("ABCD");
        return r;
    }

    private AuthDTOs.RegisterRequest registerReq() {
        AuthDTOs.RegisterRequest r = new AuthDTOs.RegisterRequest();
        r.setPhone("13800000002");
        r.setPassword("123456");
        r.setCaptchaId("cap-1");
        r.setCaptchaCode("ABCD");
        return r;
    }

    @Test
    @DisplayName("POST /login 成功 → 200 + token")
    void login_success() throws Exception {
        TUser u = new TUser();
        u.setId(7L);
        u.setUsername("u");
        u.setNickname("nick");
        u.setAvatar("/img/a.jpg");
        when(userService.login(anyString(), anyString(), anyString(), anyString())).thenReturn("mock.jwt");
        when(userService.getByPhone(anyString())).thenReturn(u);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("mock.jwt"))
                .andExpect(jsonPath("$.data.userId").value(7));
    }

    @Test
    @DisplayName("POST /login 密码太短 → 业务校验 400")
    void login_shortPassword_400() throws Exception {
        AuthDTOs.LoginRequest r = loginReq();
        r.setPassword("123");
        // 业务校验通过 R.fail(code=1001) 返回，HTTP 200
        when(userService.login(anyString(), eq("123"), anyString(), anyString()))
                .thenThrow(new BizException(ResultCode.BAD_REQUEST, "密码至少 6 位"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("POST /login 验证码错 → R.fail code=1001")
    void login_wrongCaptcha_returnsFailEnvelope() throws Exception {
        doThrow(new BizException(ResultCode.BAD_REQUEST, "图片验证码错误或已过期"))
                .when(userService).login(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.msg").value("图片验证码错误或已过期"));
    }

    @Test
    @DisplayName("POST /register 成功 → 200 + token")
    void register_success() throws Exception {
        TUser u = new TUser();
        u.setId(8L);
        u.setUsername("u");
        u.setNickname("n");
        when(userService.register(anyString(), anyString(), anyString(), anyString())).thenReturn(u);
        when(jwtUtil.generate(anyString(), anyString(), anyString(), any())).thenReturn("mock.reg.token");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("mock.reg.token"))
                .andExpect(jsonPath("$.data.userId").value(8));
    }

    @Test
    @DisplayName("POST /register 密码越界 → 业务校验失败")
    void register_passwordTooLong_400() throws Exception {
        AuthDTOs.RegisterRequest r = registerReq();
        r.setPassword("a".repeat(33));
        doThrow(new BizException(ResultCode.BAD_REQUEST, "密码长度不合法"))
                .when(userService).register(anyString(), eq("a".repeat(33)), anyString(), anyString());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("POST /register 手机号已存在 → R.fail 2003")
    void register_phoneExists() throws Exception {
        doThrow(new BizException(ResultCode.USER_ALREADY_EXISTS))
                .when(userService).register(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2003));
    }
}
