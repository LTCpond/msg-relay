package com.ltcpond.msgrelay.group.service.impl;

import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.group.model.entity.GroupMemberIndex;
import com.ltcpond.msgrelay.group.repository.GroupMemberIndexMapper;
import com.ltcpond.msgrelay.group.service.BitmapAckService;
import com.ltcpond.msgrelay.reliability.model.entity.MsgReadBitmap;
import com.ltcpond.msgrelay.reliability.repository.MsgReadBitmapMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Redis SETBIT 是群 ACK 的并发真值源；快照异步持久化到 MySQL。 */
@Slf4j
@Service
public class BitmapAckServiceImpl implements BitmapAckService {
    static final int MAX_MEMBER_COUNT = 4096;
    private static final long BITMAP_TTL_SECONDS = 30L * 24 * 3600;
    private static final String KEY_PREFIX = "msg:ack:";
    private static final DefaultRedisScript<Long> MARK_BIT = new DefaultRedisScript<>("""
            local old = redis.call('GETBIT', KEYS[1], tonumber(ARGV[1]))
            if old == 0 then
                redis.call('SETBIT', KEYS[1], tonumber(ARGV[1]), 1)
                redis.call('HINCRBY', KEYS[2], ARGV[2], 1)
                redis.call('HSET', KEYS[2], 'groupId', ARGV[3])
            end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4]))
            return old
            """, Long.class);

    @Resource private GroupMemberIndexMapper memberIndexMapper;
    @Resource private MsgReadBitmapMapper readBitmapMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private BitmapPersistenceService persistenceService;

    @Override
    public void markDelivered(Long msgId, Long userId, Long groupId) {
        mark(msgId, userId, groupId, "delivered", "deliveredCount");
    }

    @Override
    public void markRead(Long msgId, Long userId, Long groupId) {
        mark(msgId, userId, groupId, "delivered", "deliveredCount");
        mark(msgId, userId, groupId, "read", "readCount");
    }

    private void mark(Long msgId, Long userId, Long groupId, String type, String countField) {
        GroupMemberIndex member = memberIndexMapper.selectByGroupAndUser(groupId, userId);
        if (member == null || !Integer.valueOf(1).equals(member.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "用户不在群中");
        }
        int index = member.getMemberIndex();
        if (index < 0 || index >= MAX_MEMBER_COUNT) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "群成员位号超出 Bitmap 容量");
        }
        hydrateSnapshotIfNecessary(msgId, groupId);
        Long old = stringRedisTemplate.execute(MARK_BIT,
                List.of(bitmapKey(msgId, type), metaKey(msgId)),
                String.valueOf(index), countField, String.valueOf(groupId),
                String.valueOf(BITMAP_TTL_SECONDS));
        if (Long.valueOf(0).equals(old)) {
            persistenceService.persist(msgId, groupId);
            log.debug("Group ACK 0->1: msgId={}, userId={}, type={}, index={}", msgId, userId, type, index);
        }
    }

    /** Redis 租约过期或重启后，从 MySQL 快照做 SETNX 恢复，避免新 ACK 覆盖历史位。 */
    private void hydrateSnapshotIfNecessary(Long msgId, Long groupId) {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(metaKey(msgId)))) return;
        MsgReadBitmap snapshot = readBitmapMapper.selectByMsgId(msgId);
        if (snapshot == null || !groupId.equals(snapshot.getGroupId())) return;
        rawSetIfAbsent(bitmapKey(msgId, "delivered"), snapshot.getDeliveredBitmap());
        rawSetIfAbsent(bitmapKey(msgId, "read"), snapshot.getReadBitmap());
        stringRedisTemplate.opsForHash().putIfAbsent(metaKey(msgId), "groupId", groupId.toString());
        stringRedisTemplate.opsForHash().putIfAbsent(metaKey(msgId), "deliveredCount",
                String.valueOf(rawBitCount(bitmapKey(msgId, "delivered"))));
        stringRedisTemplate.opsForHash().putIfAbsent(metaKey(msgId), "readCount",
                String.valueOf(rawBitCount(bitmapKey(msgId, "read"))));
        stringRedisTemplate.expire(metaKey(msgId), BITMAP_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void rawSetIfAbsent(String key, byte[] value) {
        if (value == null) return;
        stringRedisTemplate.execute((RedisCallback<Boolean>) connection ->
                connection.stringCommands().setNX(key.getBytes(StandardCharsets.UTF_8), value));
        stringRedisTemplate.expire(key, BITMAP_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private long rawBitCount(String key) {
        Long count = stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().bitCount(key.getBytes(StandardCharsets.UTF_8)));
        return count != null ? count : 0L;
    }

    @Override public List<Long> getDeliveredUsers(Long msgId, Long groupId) {
        return decodeBitmap(groupId, loadBitmap(msgId, groupId, "delivered"));
    }
    @Override public List<Long> getReadUsers(Long msgId, Long groupId) {
        return decodeBitmap(groupId, loadBitmap(msgId, groupId, "read"));
    }
    @Override public List<Long> getUnreadUsers(Long msgId, Long groupId) {
        byte[] read = loadBitmap(msgId, groupId, "read");
        List<Long> result = new ArrayList<>();
        for (GroupMemberIndex member : memberIndexMapper.selectActiveByGroupId(groupId)) {
            if (!isSet(read, member.getMemberIndex())) result.add(member.getUserId());
        }
        return result;
    }
    @Override public int getDeliveredCount(Long msgId) { return getCount(msgId, "deliveredCount", true); }
    @Override public int getReadCount(Long msgId) { return getCount(msgId, "readCount", false); }

    private int getCount(Long msgId, String field, boolean delivered) {
        Object value = stringRedisTemplate.opsForHash().get(metaKey(msgId), field);
        if (value != null) return Integer.parseInt(value.toString());
        MsgReadBitmap snapshot = readBitmapMapper.selectByMsgId(msgId);
        if (snapshot == null) return 0;
        Integer count = delivered ? snapshot.getDeliveredCount() : snapshot.getReadCount();
        return count != null ? count : 0;
    }

    private byte[] loadBitmap(Long msgId, Long groupId, String type) {
        byte[] value = stringRedisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(bitmapKey(msgId, type).getBytes(StandardCharsets.UTF_8)));
        if (value != null) return value;
        MsgReadBitmap snapshot = readBitmapMapper.selectByMsgId(msgId);
        if (snapshot == null || !groupId.equals(snapshot.getGroupId())) return new byte[0];
        byte[] bytes = "delivered".equals(type) ? snapshot.getDeliveredBitmap() : snapshot.getReadBitmap();
        return bytes != null ? bytes : new byte[0];
    }

    private List<Long> decodeBitmap(Long groupId, byte[] bitmap) {
        List<Long> userIds = new ArrayList<>();
        for (GroupMemberIndex member : memberIndexMapper.selectActiveByGroupId(groupId)) {
            if (isSet(bitmap, member.getMemberIndex())) userIds.add(member.getUserId());
        }
        return userIds;
    }

    private boolean isSet(byte[] bitmap, int index) {
        int byteIndex = index / 8;
        int bitOffset = 7 - index % 8;
        return byteIndex < bitmap.length && (bitmap[byteIndex] & (1 << bitOffset)) != 0;
    }
    static String bitmapKey(Long msgId, String type) { return KEY_PREFIX + msgId + ":" + type; }
    static String metaKey(Long msgId) { return KEY_PREFIX + msgId + ":meta"; }
}
