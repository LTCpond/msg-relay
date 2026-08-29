package com.ltcpond.msgrelay.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 分布式 ID 生成器 — 雪花算法变种
 *
 * ID 结构 (64 bit):
 * ┌─┬─────────────────────────────────┬───────────────┬─────────────┬──────────────┐
 * │1│      41 bit 时间戳(毫秒)         │ 5 bit 数据中心  │ 5 bit 机器  │  12 bit 序列  │
 * └─┴─────────────────────────────────┴───────────────┴─────────────┴──────────────┘
 *
 * 单机每秒可生成 409.6 万个趋势递增的唯一 ID
 * EPOCH = 2023-11-15 左右，可用的 41 bit 时间戳可用约 69 年
 */
@Slf4j
@Component
public class SnowflakeIdGenerator {

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;

    private static final long EPOCH = 1700000000000L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(
            @Value("${msg-relay.snowflake.worker-id:1}") long workerId,
            @Value("${msg-relay.snowflake.datacenter-id:1}") long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId out of range");
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId out of range");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        log.info("SnowflakeIdGenerator initialized: workerId={}, datacenterId={}", workerId, datacenterId);
    }

    /** 同步方法保证同一毫秒内序列号不冲突 */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();
        // 时钟回拨检测 — 生产环境应使用 Redis 预分配来缓冲
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards, refusing to generate id");
        }
        if (timestamp == lastTimestamp) {
            // 同一毫秒内序列号自增，到 4095 后等待下一毫秒
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
