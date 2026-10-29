package com.melody.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 把 Spring Security 的认证/授权异常统一转成 R{401/403} JSON，
 * 让前端的 http 拦截器能识别并跳登录页，而不是被当 500 处理。
 */
@Component
public class JsonAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAuthHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, 401, "请先登录后再操作");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, 403, "无权限访问");
    }

    private void write(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(200); // HTTP 200，业务码表示错误，避免前端 axios 直接走 error 分支
        response.setContentType("application/json;charset=UTF-8");
        // 直接复用 R 包装：{ code:401, msg:"请先登录", data:null }
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(code, msg)));
    }
}