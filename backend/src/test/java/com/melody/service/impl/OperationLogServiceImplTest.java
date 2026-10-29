package com.melody.service.impl;

import com.melody.entity.TOperationLog;
import com.melody.mapper.TOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * OperationLogServiceImpl 单元测试 — log() 不应阻塞主业务（异常吞掉）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperationLogServiceImpl 单元测试")
class OperationLogServiceImplTest {

    @Mock private TOperationLogMapper logMapper;

    @InjectMocks
    private OperationLogServiceImpl opLogService;

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void bindRequest(String xff, String xRealIp, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (xff != null) req.addHeader("X-Forwarded-For", xff);
        if (xRealIp != null) req.addHeader("X-Real-IP", xRealIp);
        req.setRemoteAddr(remoteAddr);
        req.addHeader("User-Agent", "Mozilla/5.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    private void bindAuth(String username) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, List.of());
        auth.setDetails(new HashMap<>());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("log 成功：写入 module/event/operator/ip/ua")
    void log_success() {
        bindAuth("root");
        bindRequest(null, null, "127.0.0.1");

        opLogService.log("PRODUCT", "创建商品");

        ArgumentCaptor<TOperationLog> captor = ArgumentCaptor.forClass(TOperationLog.class);
        verify(logMapper).insert(captor.capture());
        TOperationLog log = captor.getValue();
        assertThat(log.getModule()).isEqualTo("PRODUCT");
        assertThat(log.getEvent()).isEqualTo("创建商品");
        assertThat(log.getOperator()).isEqualTo("root");
        assertThat(log.getIp()).isEqualTo("127.0.0.1");
        assertThat(log.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("log X-Forwarded-For 优先")
    void log_xffPriority() {
        bindAuth("root");
        bindRequest("203.0.113.1", "10.0.0.1", "127.0.0.1");

        opLogService.log("ORDER", "发货");

        ArgumentCaptor<TOperationLog> captor = ArgumentCaptor.forClass(TOperationLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("203.0.113.1");
    }

    @Test
    @DisplayName("log X-Forwarded-For 多级 → 取第一个")
    void log_xffMultipleLevels() {
        bindAuth("root");
        bindRequest("203.0.113.1, 198.51.100.1, 10.0.0.1", null, "127.0.0.1");

        opLogService.log("ORDER", "退款");

        ArgumentCaptor<TOperationLog> captor = ArgumentCaptor.forClass(TOperationLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("203.0.113.1");
    }

    @Test
    @DisplayName("log X-Forwarded-For=unknown → 回退到 X-Real-IP")
    void log_xffUnknownFallback() {
        bindAuth("root");
        bindRequest("unknown", "10.0.0.1", "127.0.0.1");

        opLogService.log("ORDER", "x");

        ArgumentCaptor<TOperationLog> captor = ArgumentCaptor.forClass(TOperationLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("log 无 SecurityContext → operator 默认 'admin'")
    void log_noAuth() {
        bindRequest(null, null, "127.0.0.1");

        opLogService.log("USER", "重置密码");

        ArgumentCaptor<TOperationLog> captor = ArgumentCaptor.forClass(TOperationLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperator()).isEqualTo("admin");
    }

    @Test
    @DisplayName("log 无 RequestContext → operator 仍写，IP/UA 为空")
    void log_noRequestContext() {
        bindAuth("root");
        // 不绑定 request

        opLogService.log("USER", "x");

        ArgumentCaptor<TOperationLog> captor = ArgumentCaptor.forClass(TOperationLog.class);
        verify(logMapper).insert(captor.capture());
        TOperationLog log = captor.getValue();
        assertThat(log.getOperator()).isEqualTo("root");
        assertThat(log.getIp()).isNull();
        assertThat(log.getUserAgent()).isNull();
    }

    @Test
    @DisplayName("log Mapper 抛异常 → 不向外抛出（吞掉）")
    void log_mapperFailureSwallowed() {
        bindAuth("root");
        bindRequest(null, null, "127.0.0.1");

        doThrow(new RuntimeException("DB down")).when(logMapper).insert(any());

        // 不应抛异常
        opLogService.log("USER", "x");

        verify(logMapper).insert(any());
    }
}
