package com.ltcpond.msgrelay.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ltcpond.msgrelay.common.lock.LockTemplate;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 多级缓存框架 — Caffeine(本地) → Redis(分布式) → MySQL(持久层)
 *
 * 防缓存穿透: DB 返回 null 时缓存空标记(NULL_MARKER)到 Redis，短 TTL 避免重复查库
 * 防缓存击穿: get() 方法内置分布式锁，同一 key 只有一个线程回源 DB
 * 防缓存雪崩: randomTtl() 给 Redis 过期时间加 20% 随机浮动，避免同时过期
 */
@Component
public class MultiLevelCache {

    /** 空值哨兵 — 用 HashMap 表示，可被 Jackson 序列化/反序列化，用于防止缓存穿透 */
    private static final Map<String, Boolean> NULL_MARKER = Map.of("__msg_relay_null__", true);

    /** 缓存命中标记 — getFromCache 返回此对象表示命中空值缓存，区别于 null(未命中) */
    private static final Object NULL_PRESENT = new Object();

    /** 空值缓存时长（秒） — 短 TTL，避免长期占用 Redis 内存 */
    private static final long NULL_TTL_SECONDS = 30;

    private final Cache<String, Object> caffeineCache;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private LockTemplate lockTemplate;

    public MultiLevelCache() {
        // Caffeine 作为 L1 本地缓存，最大 1 万条，5 分钟过期
        this.caffeineCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    /**
     * 不走 DB，只查 Caffeine + Redis。
     * 返回 null 表示缓存未命中，返回 NULL_PRESENT 表示已缓存空值（调用方应返回 null）
     */
    @SuppressWarnings("unchecked")
    private <T> T getFromCache(String key, Class<T> clazz) {
        Object value = caffeineCache.getIfPresent(key);
        if (value != null) {
            if (isNull(value)) {
                return (T) NULL_PRESENT;
            }
            return (T) value;
        }
        value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            caffeineCache.put(key, value);
            if (isNull(value)) {
                return (T) NULL_PRESENT;
            }
            return (T) value;
        }
        return null;
    }

    /** 不走 DB，只查 Caffeine + Redis；命中空值缓存时返回 null */
    public <T> T get(String key, Class<T> clazz) {
        T result = getFromCache(key, clazz);
        return result == NULL_PRESENT ? null : result;
    }

    /** 三级缓存读取 — 加分布式锁回源 DB，防止缓存击穿；DB 返回 null 时缓存空标记防穿透 */
    public <T> T get(String key, Class<T> clazz, Function<String, T> dbLoader, long redisTtlSeconds) {
        T cached = getFromCache(key, clazz);
        if (cached == NULL_PRESENT) {
            return null;
        }
        if (cached != null) {
            return cached;
        }
        String lockKey = "cache:" + key;
        try {
            return lockTemplate.executeWithLock(lockKey, 5, 10, () -> {
                T lockedValue = getFromCache(key, clazz);
                if (lockedValue == NULL_PRESENT) {
                    return null;
                }
                if (lockedValue != null) {
                    return lockedValue;
                }
                T dbValue = dbLoader.apply(key);
                if (dbValue != null) {
                    redisTemplate.opsForValue().set(key, dbValue, randomTtl(redisTtlSeconds), TimeUnit.SECONDS);
                    caffeineCache.put(key, dbValue);
                } else {
                    redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_TTL_SECONDS, TimeUnit.SECONDS);
                    caffeineCache.put(key, NULL_MARKER);
                }
                return dbValue;
            });
        } catch (RuntimeException e) {
            T dbValue = dbLoader.apply(key);
            if (dbValue != null) {
                caffeineCache.put(key, dbValue);
                redisTemplate.opsForValue().set(key, dbValue, randomTtl(redisTtlSeconds), TimeUnit.SECONDS);
            } else {
                caffeineCache.put(key, NULL_MARKER);
                redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_TTL_SECONDS, TimeUnit.SECONDS);
            }
            return dbValue;
        }
    }

    public void evict(String key) {
        caffeineCache.invalidate(key);
        redisTemplate.delete(key);
    }

    /** 过期时间加 20% 随机浮动，防止缓存雪崩 */
    private long randomTtl(long baseSeconds) {
        double factor = 0.8 + Math.random() * 0.4;
        return (long) (baseSeconds * factor);
    }

    /** 判断值是否为空标记（支持 Caffeine 直接比较和 Redis 反序列化后的 Map 比较） */
    private boolean isNull(Object value) {
        if (value == NULL_MARKER) {
            return true;
        }
        if (value instanceof Map) {
            return Boolean.TRUE.equals(((Map<?, ?>) value).get("__msg_relay_null__"));
        }
        return false;
    }
}
