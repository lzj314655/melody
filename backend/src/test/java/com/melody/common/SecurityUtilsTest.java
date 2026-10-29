package com.melody.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityUtils 静态方法测试：通过 SecurityContextHolder 操作上下文。
 */
@DisplayName("SecurityUtils 单元测试")
class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("无认证上下文 → currentId/currentUsername 返回 null")
    void noAuth() {
        assertThat(SecurityUtils.currentId()).isNull();
        assertThat(SecurityUtils.currentUsername()).isNull();
    }

    @Test
    @DisplayName("anonymousUser → currentId 返回 null")
    void anonymous() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityUtils.currentId()).isNull();
    }

    @Test
    @DisplayName("details.userId → currentId 直接读取")
    void detailsUserId() {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", 42L);
        details.put("username", "alice");

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("42", "ignored",
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityUtils.currentId()).isEqualTo(42L);
        assertThat(SecurityUtils.currentUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("principal 是数字字符串 → 兜底解析")
    void principalAsLongString() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("999", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityUtils.currentId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("principal 是字符串非数字 → currentId 返回 null，currentUsername 返回 principal")
    void principalAsString() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("alice", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityUtils.currentId()).isNull();
        assertThat(SecurityUtils.currentUsername()).isEqualTo("alice");
    }
}
