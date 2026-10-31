package com.melody.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtil 单元测试：不依赖 Spring 容器（直接 @PostConstruct）。
 */
@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-secret-key-melody-shop-32bytes-please");
        ReflectionTestUtils.invokeMethod(jwtUtil, "init");
    }

    @Test
    @DisplayName("generate + parse 完整往返")
    void generateAndParse() {
        String token = jwtUtil.generate("42", "alice", "user", List.of("product:read"));
        assertThat(token).isNotBlank().contains(".");

        Claims claims = jwtUtil.parse(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("username")).isEqualTo("alice");
        assertThat(claims.get("type")).isEqualTo("user");
        Object permsClaim = claims.get("perms");
        assertThat(permsClaim).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        List<Object> permsList = (List<Object>) permsClaim;
        assertThat(permsList).containsExactly("product:read");
    }

    @Test
    @DisplayName("admin 类型 + 权限列表")
    void adminToken() {
        String token = jwtUtil.generate("1", "admin", "admin", List.of("dashboard:view", "order:read"));
        Claims claims = jwtUtil.parse(token);
        assertThat(claims.get("type")).isEqualTo("admin");
        assertThat((List<?>) claims.get("perms")).asList().hasSize(2);
    }

    @Test
    @DisplayName("空权限列表也能正确序列化")
    void emptyPermissions() {
        String token = jwtUtil.generate("1", "u", "user", List.of());
        Claims claims = jwtUtil.parse(token);
        assertThat((List<?>) claims.get("perms")).isEmpty();
    }

    @Test
    @DisplayName("篡改 token → JwtException")
    void tamperedToken_throws() {
        String token = jwtUtil.generate("1", "u", "user", List.of());
        // 把 token 最后一个字符改了
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');
        assertThatThrownBy(() -> jwtUtil.parse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("短 secret → 启动失败（不再自动补齐弱密钥）")
    void shortSecret_rejected() {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", "abc"); // 仅 3 字节
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(util, "init"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("空 secret → 启动失败")
    void blankSecret_rejected() {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", "   ");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(util, "init"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT");
    }
}
