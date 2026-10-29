package com.melody.config;

import com.melody.common.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器：解析 Authorization: Bearer xxx，把权限写入 SecurityContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parse(token);
                String id = claims.getSubject();
                String username = (String) claims.get("username");
                String type = (String) claims.get("type");
                @SuppressWarnings("unchecked")
                List<String> perms = (List<String>) claims.get("perms");

                java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                // 主角色：user / admin
                if (type != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + type.toUpperCase()));
                if (perms != null) perms.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

                java.util.Map<String, Object> details = new java.util.HashMap<>();
                if (id != null) {
                    try { details.put("userId", Long.parseLong(id)); } catch (NumberFormatException ignored) {}
                }
                details.put("username", username);
                details.put("type", type);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username == null ? id : username, null, authorities);
                auth.setDetails(details);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ex) {
                // token 无效或过期，让 Spring Security 后续拒掉；这里只清 context
                SecurityContextHolder.clearContext();
                log.debug("[JWT] parse failed: {}", ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}