package com.ltcpond.msgrelay.common.config;

import com.ltcpond.msgrelay.common.utils.JwtUtils;
import com.ltcpond.msgrelay.common.auth.LoginSessionValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * JWT 认证过滤器 — ACCESS 凭证和设备登录会话共同鉴权
 *
 * 过滤链:
 * 1. 白名单路径（登录/刷新 Token）直接放行
 * 2. 从 Authorization: Bearer xxx 请求头提取 token
 * 3. 校验 ACCESS 类型和设备会话，将 userId/deviceId 写入 request 属性
 * 4. token 无效或过期返回 401
 *
 * 每个请求校验设备登录会话（Redis 缓存优先），撤销后下一次请求返回 401。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final LoginSessionValidator loginSessionValidator;

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/user/auth/login",
            "/api/user/auth/refresh"
    );

    public JwtAuthenticationFilter(JwtUtils jwtUtils, LoginSessionValidator loginSessionValidator) {
        this.jwtUtils = jwtUtils;
        this.loginSessionValidator = loginSessionValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未授权\"}");
            return;
        }

        try {
            Claims claims = jwtUtils.parseAccessToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            String deviceId = claims.get("deviceId", String.class);
            if (!loginSessionValidator.isValid(userId, deviceId)) {
                throw new JwtException("登录状态已失效");
            }
            request.setAttribute("userId", userId);
            request.setAttribute("deviceId", deviceId);
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.contains(path);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
