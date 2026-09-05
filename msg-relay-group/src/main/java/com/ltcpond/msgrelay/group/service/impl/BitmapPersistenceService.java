package com.ltcpond.msgrelay.group.service.impl;

import com.ltcpond.msgrelay.common.lock.LockTemplate;
import com.ltcpond.msgrelay.reliability.repository.MsgReadBitmapMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

/** 将 Redis ACK Bitmap 的最新完整快照串行写回 MySQL。 */
@Slf4j
@Service
public class BitmapPersistenceService {
    @Resource private StringRedisTemplate redisTemplate;
    @Resource private MsgReadBitmapMapper mapper;
    @Resource private LockTemplate lockTemplate;

    @Async("bitmapPersistenceExecutor")
    public void persist(Long msgId, Long groupId) {
        try {
            lockTemplate.executeWithLock("bitmap:persist:" + msgId, 5, 10, () -> {
                byte[] delivered = rawGet(BitmapAckServiceImpl.bitmapKey(msgId, "delivered"));
                byte[] read = rawGet(BitmapAckServiceImpl.bitmapKey(msgId, "read"));
                mapper.upsertSnapshot(msgId, groupId, delivered, count(msgId, "deliveredCount"),
                        read, count(msgId, "readCount"));
                return null;
            });
        } catch (Exception e) {
            log.error("Persist ACK bitmap failed: msgId={}", msgId, e);
        }
    }
    private byte[] rawGet(String key) {
        byte[] value = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
        return value != null ? value : new byte[0];
    }
    private int count(Long msgId, String field) {
        Object value = redisTemplate.opsForHash().get(BitmapAckServiceImpl.metaKey(msgId), field);
        return value == null ? 0 : Integer.parseInt(value.toString());
    }
}
