package com.melody.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 取当前登录用户/管理员 ID
 */
public class SecurityUtils {

    private SecurityUtils() {}

    public static Long currentId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        // 优先从 auth details 里取（JwtAuthFilter 写入的 userId）
        Object details = auth.getDetails();
        if (details instanceof java.util.Map<?, ?> map) {
            Object v = ((java.util.Map<String, Object>) map).get("userId");
            if (v instanceof Number n) return n.longValue();
        }
        // 兜底：解析 principal（早期 token 或非 JWT 场景）
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        // 优先从 details 取 username
        Object details = auth.getDetails();
        if (details instanceof java.util.Map<?, ?> map) {
            Object v = ((java.util.Map<String, Object>) map).get("username");
            if (v instanceof String s && !s.isBlank()) return s;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof org.springframework.security.core.userdetails.UserDetails ud
                ? ud.getUsername()
                : String.valueOf(principal);
    }
}