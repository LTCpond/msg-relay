package com.ltcpond.msgrelay.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** 仅缓存设备会话状态，不保存 Token，也不复用 Presence key。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSessionCache {
    private final StringRedisTemplate redisTemplate;

    @Value("${msg-relay.login-session.cache-ttl-seconds:300}")
    private long cacheTtlSeconds = 300;
    @Value("${msg-relay.login-session.negative-cache-ttl-seconds:30}")
    private long negativeCacheTtlSeconds = 30;

    private static final DefaultRedisScript<Long> FILL = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return 1
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    public boolean isValid(Long userId, String deviceId, BooleanSupplier databaseLookup) {
        String key = key(userId, deviceId);
        String loader = "LOADING:" + UUID.randomUUID();
        boolean ownsLoad;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if ("1".equals(cached)) {
                return true;
            }
            if ("0".equals(cached)) {
                return false;
            }
            // 写事务尚未完成或另一请求正在加载时，只回源，不回填。
            ownsLoad = cached == null && Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(key, loader, Duration.ofSeconds(10)));
        } catch (DataAccessException e) {
            log.warn("登录会话缓存不可用，回源 MySQL: userId={}, deviceId={}", userId, deviceId);
            return databaseLookup.getAsBoolean();
        }

        boolean valid;
        try {
            valid = databaseLookup.getAsBoolean();
        } catch (RuntimeException e) {
            if (ownsLoad) {
                endChange(userId, deviceId, loader);
            }
            throw e;
        }
        if (ownsLoad) {
            try {
                // 只有原加载标记仍存在才回填，踢人/重新登录后的旧查询不能覆盖新状态。
                redisTemplate.execute(FILL, List.of(key), loader, valid ? "1" : "0",
                        Long.toString(Math.max(1, valid ? cacheTtlSeconds : negativeCacheTtlSeconds)));
            } catch (DataAccessException e) {
                log.warn("登录会话缓存回填失败: userId={}, deviceId={}", userId, deviceId);
            }
        }
        return valid;
    }

    /** 提交前覆盖旧缓存，失败必须使数据库事务回滚。 */
    public void beginChange(Long userId, String deviceId, String marker) {
        // 不设 TTL：提交结果未知时不得重新使用旧缓存。进程崩溃留下标记也只会回源。
        redisTemplate.opsForValue().set(key(userId, deviceId), marker);
    }

    /** 提交/回滚完成后允许下次请求重建缓存；不能删除后续事务的标记。 */
    public void endChange(Long userId, String deviceId, String marker) {
        try {
            redisTemplate.execute(REMOVE, List.of(key(userId, deviceId)), marker);
        } catch (DataAccessException e) {
            // 保留 UPDATING 状态，Redis 恢复后仍回源，不会把旧正缓存当作有效登录。
            log.warn("登录会话缓存清理失败，保留回源标记: userId={}, deviceId={}", userId, deviceId);
        }
    }

    private String key(Long userId, String deviceId) {
        return "login:session:" + userId + ":" + deviceId;
    }
}
