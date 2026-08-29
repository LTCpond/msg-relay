package com.ltcpond.msgrelay.common.ratelimit;

import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 限流切面 — 拦截 @RateLimit 注解的方法
 *
 * 原理: Redis INCR + TTL 实现固定窗口计数
 * 1. 根据 keyType 生成不同的 Redis key（方法/用户/设备/IP）
 * 2. INCR 自增计数，首次调用时设置 1 秒过期
 * 3. 计数超过 qps 阈值则抛出限流异常
 *
 * 注意: 这是固定窗口算法，边界处可能短暂超限。生产环境建议用 Sentinel 实现滑动窗口。
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint jp, RateLimit rateLimit) throws Throwable {
        String key = buildKey(jp, rateLimit);
        Long current = redisTemplate.opsForValue().increment(key, 1);
        // 首次请求设置 1 秒过期，形成 1 秒的计数窗口
        if (current != null && current == 1) {
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }
        if (current != null && current > rateLimit.qps()) {
            log.warn("Rate limit exceeded: key={}, qps={}, current={}", key, rateLimit.qps(), current);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, rateLimit.message());
        }
        return jp.proceed();
    }

    /** 根据 keyType 构建限流 key */
    private String buildKey(ProceedingJoinPoint jp, RateLimit rateLimit) {
        String prefix = "ratelimit:";
        return switch (rateLimit.keyType()) {
            case DEVICE -> prefix + "device:" + getDeviceId();
            case USER -> prefix + "user:" + getUserId();
            case IP -> prefix + "ip:" + getClientIp();
            default -> prefix + jp.getSignature().toLongString();
        };
    }

    /** 从 request 中获取 deviceId */
    private String getDeviceId() {
        HttpServletRequest request = getRequest();
        if (request == null) return "unknown";
        String deviceId = request.getParameter("deviceId");
        return deviceId != null ? deviceId : "unknown";
    }

    /** 从 request attribute 中获取 userId（由认证拦截器设置） */
    private String getUserId() {
        HttpServletRequest request = getRequest();
        if (request == null) return "unknown";
        Object userId = request.getAttribute("userId");
        return userId != null ? userId.toString() : "unknown";
    }

    /** 获取客户端 IP */
    private String getClientIp() {
        HttpServletRequest request = getRequest();
        if (request == null) return "unknown";
        return request.getRemoteAddr();
    }

    /** 获取当前请求 */
    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
