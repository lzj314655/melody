package com.melody.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具（HS256）
 *
 * Claims：
 *   sub      = adminId 或 userId（String）
 *   username
 *   type     = "admin" / "user"
 *   perms    = 权限 code 列表（仅 admin 有）
 *   exp / iat
 */
@Component
public class JwtUtil {

    /**
     * 必须由环境变量 / application-local.yml 注入，禁止在代码里写默认值。
     * 启动时校验长度，避免用弱密钥签发 JWT。
     */
    @Value("${melody.jwt.secret}")
    private String secret;

    /** Token 有效期 7 天（PRD §5.2） */
    private static final long EXPIRE_MS = 7L * 24 * 3600 * 1000;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // JJWT 0.12 / HS256 要求 key 至少 256 bit（32 字节）
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "缺少 JWT 密钥：请设置环境变量 JWT_SECRET（或 application-local.yml 的 melody.jwt.secret）");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET 太短：至少需要 32 字节，当前 " + bytes.length + " 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** 生成 Token */
    public String generate(String id, String username, String type, List<String> permissions) {
        Date now = new Date();
        return Jwts.builder()
                .subject(id)
                .claim("username", username)
                .claim("type", type)
                .claim("perms", permissions)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRE_MS))
                .signWith(key)
                .compact();
    }

    /** 解析（失败抛 JwtException） */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}