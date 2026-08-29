package com.ltcpond.msgrelay.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 — 无状态认证的核心
 *
 * 双 Token 机制:
 * - accessToken: 短期有效（默认 2h），用于 API 鉴权
 * - refreshToken: 长期有效（默认 7d），用于无感续期
 *
 * 流程: 登录 → 返回双 token → API 请求带 accessToken → 过期后用 refreshToken 换新
 */
@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expire}")
    private long accessTokenExpire;

    @Value("${jwt.refresh-token-expire}")
    private long refreshTokenExpire;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId) {
        return generateToken(userId, null, accessTokenExpire);
    }

    /** 生成 refresh token，将 deviceId 编码到 claims 中以支持踢人后失效 */
    public String generateRefreshToken(Long userId, String deviceId) {
        return generateToken(userId, deviceId, refreshTokenExpire);
    }

    private String generateToken(Long userId, String deviceId, long expireSeconds) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000));
        if (deviceId != null) {
            builder.claim("deviceId", deviceId);
        }
        return builder.signWith(getKey()).compact();
    }

    /** 从 token 中提取 deviceId，仅 refresh token 携带此 claim */
    public String getDeviceId(String token) {
        return parseToken(token).get("deviceId", String.class);
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Token已过期", e);
        } catch (JwtException e) {
            throw new RuntimeException("Token无效", e);
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

}
