package com.ltcpond.msgrelay.common.lock;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
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

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /** 自旋获取锁，最多等待 waitSeconds 秒，锁定后持有 leaseSeconds 秒自动释放 */
    public <T> T executeWithLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        String lockId = UUID.randomUUID().toString();
        String key = "lock:" + lockKey;
        long deadline = System.currentTimeMillis() + waitSeconds * 1000;

        while (System.currentTimeMillis() < deadline) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, lockId, leaseSeconds, TimeUnit.SECONDS);
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
                throw new RuntimeException("获取锁被中断: " + lockKey, e);
            }
        }
        throw new RuntimeException("获取锁超时: " + lockKey);
    }

    /** 比对 lockId 再删除，防止误删其他线程持有的锁 */
    private void release(String key, String lockId) {
        String current = (String) redisTemplate.opsForValue().get(key);
        if (lockId.equals(current)) {
            redisTemplate.delete(key);
        }
    }
}
