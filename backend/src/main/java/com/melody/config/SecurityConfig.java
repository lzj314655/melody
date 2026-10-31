package com.melody.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置（JWT 无状态 + @PreAuthorize）
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)   // 启用 @PreAuthorize / @PostAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JsonAuthHandlers jsonAuthHandlers;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // PRD §5.2: BCrypt 强度 10
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(jsonAuthHandlers)
                .accessDeniedHandler(jsonAuthHandlers))
            .authorizeHttpRequests(authz -> authz
                // 公开端点（带 /api 前缀，匹配实际请求路径）
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/captcha",
                    "/api/captcha/**",
                    "/api/admin/auth/login",
                    "/api/behavior/report",
                    "/api/behavior/**",
                    "/api/product/list",
                    "/api/product/detail/**",
                    "/api/product/categories",
                    "/api/sku",
                    "/api/sku/**",
                    "/api/coupon/available",
                    "/api/banner/list",
                    "/api/announcement/list",
                    // 游客也要能看推荐与商品评价；写操作在 Controller/Service 内部自行校验登录态
                    "/api/recommend/**",
                    "/api/review/**",
                    // 后台实时通知 SSE 流（事件仅含提示信息，无订单明细）
                    "/api/notify/stream",
                    // 支付宝异步通知（沙箱本地打不到，部署后生效；必须公开，无 JWT）
                    "/api/wallet/recharge/notify",
                    "/api/order/alipay/notify",
                    // 内部服务接口：由 X-After-Sale-Token 在 Controller 内校验
                    "/api/internal/**",
                    // 本地商品图片（爬虫产物，无需鉴权）
                    "/img/**"
                ).permitAll()
                // 后台接口需 ROLE_SUPER_ADMIN 或 ROLE_OPERATOR
                .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "OPERATOR")
                // 前台用户接口需 ROLE_USER（防止 admin token 误用访问前台）
                .requestMatchers("/api/cart/**", "/api/order/**", "/api/coupon/claim",
                                 "/api/coupon/mine", "/api/address/**", "/api/favorite/**",
                                 "/api/wallet/**", "/api/user/me", "/api/user/**",
                                 "/api/after-sales/**",
                                 "/api/checkout/**", "/api/behavior/report")
                    .hasRole("USER")
                // 其他接口需登录
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 全局 CORS：允许 vite / 任意前端跨域调用。
     * 注意：Spring Security 6 的 CORS 由内置 CorsFilter 处理，
     * 需用 CorsConfigurationSource bean 替代旧的 cors() lambda 写法。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}