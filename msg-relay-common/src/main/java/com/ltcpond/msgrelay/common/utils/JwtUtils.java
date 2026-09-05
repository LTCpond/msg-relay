package com.ltcpond.msgrelay.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** 双 JWT 身份凭证；是否允许使用由设备登录会话决定。Token 本身不落库。 */
@Component
public class JwtUtils {
    public static final String ACCESS = "ACCESS";
    public static final String REFRESH = "REFRESH";

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.access-token-expire}")
    private long accessTokenExpire;
    @Value("${jwt.refresh-token-expire}")
    private long refreshTokenExpire;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public long getAccessTokenExpire() {
        return accessTokenExpire;
    }

    public String generateAccessToken(Long userId, String deviceId) {
        return generateToken(userId, deviceId, ACCESS, accessTokenExpire);
    }

    public String generateRefreshToken(Long userId, String deviceId) {
        return generateToken(userId, deviceId, REFRESH, refreshTokenExpire);
    }

    private String generateToken(Long userId, String deviceId, String tokenType, long expireSeconds) {
        if (userId == null || !StringUtils.hasText(deviceId)) {
            throw new IllegalArgumentException("userId 和 deviceId 不能为空");
        }
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("deviceId", deviceId)
                .claim("tokenType", tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000))
                .signWith(getKey())
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return parseTypedToken(token, ACCESS);
    }

    public Claims parseRefreshToken(String token) {
        return parseTypedToken(token, REFRESH);
    }

    private Claims parseTypedToken(String token, String expectedType) {
        Claims claims = parseToken(token);
        if (!expectedType.equals(claims.get("tokenType", String.class))
                || !StringUtils.hasText(claims.get("deviceId", String.class))
                || claims.getExpiration() == null) {
            throw new JwtException("Token 类型或必要声明无效");
        }
        try {
            Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new JwtException("Token 用户身份无效", e);
        }
        return claims;
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, ACCESS);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, REFRESH);
    }

    private boolean validateToken(String token, String type) {
        try {
            parseTypedToken(token, type);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public String getDeviceId(String token) {
        return parseToken(token).get("deviceId", String.class);
    }

    public String getTokenType(String token) {
        return parseToken(token).get("tokenType", String.class);
    }
}
