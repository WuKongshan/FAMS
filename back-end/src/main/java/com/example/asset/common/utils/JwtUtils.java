package com.example.asset.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 令牌工具类
 */
@Component
public class JwtUtils {

    /** HS256 要求密钥不少于 256 位（32 字节） */
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * 启动即校验密钥。
     * <p>密钥现在是环境变量注入的，缺失时若不在此处失败，会在第一次登录才抛出
     * {@code WeakKeyException}，定位成本高。这里提前失败并给出可操作的提示。</p>
     */
    @PostConstruct
    void validateSecret() {
        int length = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT 签名密钥未配置或长度不足：" + length + " 字节（HS256 要求 >= " + MIN_SECRET_BYTES + "）。\n"
                            + "请设置环境变量 JWT_SECRET 后重启，例如：\n"
                            + "  PowerShell:  $env:JWT_SECRET = \"<至少32字节的随机串>\"\n"
                            + "  生成随机串:  openssl rand -base64 48  （或任何 >=32 字节的随机字符串）\n"
                            + "  IDEA:        Run/Debug Configurations -> Environment variables 中配置\n"
                            + "变量清单见 back-end/.env.example");
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token
     */
    public String generateToken(Long userId, String username, Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .id(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    /**
     * 从 Token 中解析 Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getId());
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }
}
