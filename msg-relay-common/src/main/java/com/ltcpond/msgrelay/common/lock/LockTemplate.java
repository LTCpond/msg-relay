package com.ltcpond.msgrelay.common.lock;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁模板 — 基于 Redis SETNX + TTL 实现
 *
 * 原理: SET key UUID NX EX leaseSeconds
 * - NX: 仅当 key 不存在时设置（互斥）
 * - UUID: 锁持有者标识，释放时比对防误删
 * - TTL: 自动过期，防止死锁
 *
 * 注意: 这是简化实现（无 Redisson 的看门狗自动续期），长任务需确保 leaseSeconds 足够
 */
@Component
public class LockTemplate {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /** 自旋获取锁，最多等待 waitSeconds 秒，锁定后持有 leaseSeconds 秒自动释放 */
    public <T> T executeWithLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        String lockId = UUID.randomUUID().toString();
        String key = "lock:" + lockKey;
        long deadline = System.currentTimeMillis() + waitSeconds * 1000;

        while (System.currentTimeMillis() < deadline) {
            Boolean acquired;
            try {
                acquired = redisTemplate.opsForValue()
                        .setIfAbsent(key, lockId, leaseSeconds, TimeUnit.SECONDS);
            } catch (RuntimeException e) {
                throw new LockAcquisitionException("获取锁失败: " + lockKey, e);
            }
            if (Boolean.TRUE.equals(acquired)) {
                try {
                    return supplier.get();
                } finally {
                    release(key, lockId);
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LockAcquisitionException("获取锁被中断: " + lockKey, e);
            }
        }
        throw new LockAcquisitionException("获取锁超时: " + lockKey);
    }

    /** Lua 原子比对并删除，避免 GET 与 DEL 之间锁过期后误删新持有者的锁。 */
    private void release(String key, String lockId) {
        redisTemplate.execute(RELEASE_SCRIPT, java.util.Collections.singletonList(key), lockId);
    }
}
