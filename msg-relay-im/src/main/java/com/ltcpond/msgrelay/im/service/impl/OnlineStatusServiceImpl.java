package com.ltcpond.msgrelay.im.service.impl;

import com.ltcpond.msgrelay.im.config.PresenceConfig;
import com.ltcpond.msgrelay.im.service.OnlineStatusService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 在线状态服务 — ZSET + Lua 方案（无 KEYS 阻塞）
 *
 * 核心思路：
 * - 用 Redis ZSET 维护"用户在线设备集合"，member=deviceId，score=expireAt
 * - 用 Lua 脚本原子执行"清理过期 + 续租/注册 + 返回在线设备数"
 * - 用 Redis TIME 统一时钟，避免服务节点时钟偏差
 *
 * 与旧方案的区别：
 * - 旧方案：每个设备一个 key（online:{userId}:{channelId}），用 KEYS 扫描判断在线
 * - 新方案：每用户一个 ZSET，用 ZCARD/ZRANGEBYSCORE 判断在线，O(logN) 复杂度
 *
 * 租约机制（偏准）：
 * - leaseTTL=40s，每次收到有效 Pong 时续租
 * - 断开后 presence 在 leaseTTL 内自然过期，不做 5s 窄窗口（避免闪断）
 */
@Slf4j
@Service
public class OnlineStatusServiceImpl implements OnlineStatusService {

    @Resource
    private StringRedisTemplate redisTemplate;

    @Resource
    private PresenceConfig presenceConfig;

    /** 节点标识（多节点时通过 msg-relay.netty.node-id 或 NODE_ID 环境变量指定） */
    @org.springframework.beans.factory.annotation.Value("${msg-relay.netty.node-id:node-1}")
    private String nodeId;

    /** ZSET key 前缀：online:u:{userId} */
    private static final String USER_SET_PREFIX = "online:u:";

    /** 设备元信息 key 前缀：online:dev:{userId}:{deviceId} */
    private static final String DEV_INFO_PREFIX = "online:dev:";

    /**
     * Lua: touch（注册/续租）
     *
     * 原子操作：
     * 1. Redis TIME 取当前时间（统一时钟，避免节点偏差）
     * 2. 清理 ZSET 中已过期的设备
     * 3. 将当前设备加入 ZSET，score=now+ttl
     * 4. 写入设备元信息（nodeId、channelId）并设置 TTL
     * 5. 返回清理后 ZSET 大小（在线设备数）
     *
     * KEYS[1]=用户 ZSET key
     * ARGV[1]=deviceId, ARGV[2]=nodeId, ARGV[3]=channelId, ARGV[4]=ttl秒
     */
    private static final String LUA_TOUCH = """
            local userKey = KEYS[1]
            local deviceId = ARGV[1]
            local nodeId = ARGV[2]
            local channelId = ARGV[3]
            local ttl = tonumber(ARGV[4])

            -- 用 Redis TIME 统一时钟，避免服务节点 NTP 偏差
            local time = redis.call('TIME')
            local now = tonumber(time[1])
            local expireAt = now + ttl

            -- 清理 ZSET 中已过期的设备（score < now 的全部移除）
            redis.call('ZREMRANGEBYSCORE', userKey, '-inf', now)

            -- 注册/续租当前设备：member=deviceId，score=expireAt
            redis.call('ZADD', userKey, expireAt, deviceId)

            -- 写入设备元信息（nodeId、channelId），并设置 TTL
            local devKey = 'online:dev:' .. KEYS[1]:sub(#('online:u:') + 1) .. ':' .. deviceId
            redis.call('HSET', devKey, 'nodeId', nodeId, 'channelId', channelId, 'lastSeen', now)
            redis.call('EXPIRE', devKey, ttl)

            -- 设置 ZSET key 的 TTL（兜底清理，避免 key 永驻）
            redis.call('EXPIRE', userKey, ttl + 60)

            -- 返回当前在线设备数
            return redis.call('ZCARD', userKey)
            """;

    /** 心跳只续租，不覆盖 online() 写入的 nodeId/channelId 路由。 */
    private static final String LUA_HEARTBEAT = """
            local userKey = KEYS[1]
            local devKey = KEYS[2]
            local deviceId = ARGV[1]
            local ttl = tonumber(ARGV[2])
            local time = redis.call('TIME')
            local now = tonumber(time[1])
            if redis.call('EXISTS', devKey) == 0 then
                return 0
            end
            redis.call('ZREMRANGEBYSCORE', userKey, '-inf', now)
            redis.call('ZADD', userKey, now + ttl, deviceId)
            redis.call('HSET', devKey, 'lastSeen', now)
            redis.call('EXPIRE', devKey, ttl)
            redis.call('EXPIRE', userKey, ttl + 60)
            return 1
            """;

    /**
     * Lua: 离线（移除设备）
     *
     * 原子操作：
     * 1. 清理 ZSET 中已过期的设备
     * 2. 从 ZSET 中移除指定设备
     * 3. 删除设备元信息 key
     * 4. 返回剩余在线设备数
     *
     * KEYS[1]=用户 ZSET key
     * ARGV[1]=deviceId
     */
    private static final String LUA_OFFLINE = """
            local userKey = KEYS[1]
            local deviceId = ARGV[1]

            -- 用 Redis TIME 获取当前时间
            local time = redis.call('TIME')
            local now = tonumber(time[1])

            -- 清理已过期设备
            redis.call('ZREMRANGEBYSCORE', userKey, '-inf', now)

            -- 移除指定设备
            redis.call('ZREM', userKey, deviceId)

            -- 删除设备元信息
            local devKey = 'online:dev:' .. KEYS[1]:sub(#('online:u:') + 1) .. ':' .. deviceId
            redis.call('DEL', devKey)

            -- 返回剩余在线设备数
            return redis.call('ZCARD', userKey)
            """;

    /**
     * Lua: 判断是否在线
     *
     * KEYS[1]=用户 ZSET key
     * 无 ARGV
     */
    private static final String LUA_IS_ONLINE = """
            local userKey = KEYS[1]
            local time = redis.call('TIME')
            local now = tonumber(time[1])
            redis.call('ZREMRANGEBYSCORE', userKey, '-inf', now)
            local count = redis.call('ZCARD', userKey)
            if count > 0 then
                return 1
            else
                return 0
            end
            """;

    /**
     * Lua: 获取在线设备列表
     *
     * KEYS[1]=用户 ZSET key
     * 无 ARGV
     */
    private static final String LUA_LIST_DEVICES = """
            local userKey = KEYS[1]
            local time = redis.call('TIME')
            local now = tonumber(time[1])
            redis.call('ZREMRANGEBYSCORE', userKey, '-inf', now)
            return redis.call('ZRANGE', userKey, 0, -1)
            """;

    private static final String LUA_BATCH_ONLINE = """
            local time = redis.call('TIME')
            local now = tonumber(time[1])
            local result = {}
            for _, key in ipairs(KEYS) do
                redis.call('ZREMRANGEBYSCORE', key, '-inf', now)
                if redis.call('ZCARD', key) > 0 then
                    table.insert(result, 1)
                else
                    table.insert(result, 0)
                end
            end
            return result
            """;

    /** 一次 Lua 批量完成 user -> active devices -> nodeId 路由解析，避免逐用户 RTT。 */
    private static final String LUA_BATCH_NODES = """
            local time = redis.call('TIME')
            local now = tonumber(time[1])
            local result = {}
            for i, userKey in ipairs(KEYS) do
                redis.call('ZREMRANGEBYSCORE', userKey, '-inf', now)
                local devices = redis.call('ZRANGEBYSCORE', userKey, now, '+inf')
                local seen = {}
                for _, deviceId in ipairs(devices) do
                    local devKey = 'online:dev:' .. ARGV[i] .. ':' .. deviceId
                    local targetNode = redis.call('HGET', devKey, 'nodeId')
                    if targetNode and not seen[targetNode] then
                        table.insert(result, ARGV[i])
                        table.insert(result, targetNode)
                        seen[targetNode] = true
                    end
                end
            end
            return result
            """;

    @Override
    public void online(Long userId, String deviceId, String channelId) {
        String key = USER_SET_PREFIX + userId;
        long ttl = presenceConfig.getLeaseTtlSeconds();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_TOUCH, Long.class);
        Long onlineCount = redisTemplate.execute(script, Collections.singletonList(key),
                deviceId, nodeId, channelId, String.valueOf(ttl));

        log.info("设备上线: userId={}, deviceId={}, channelId={}, 在线设备数={}",
                userId, deviceId, channelId, onlineCount);
    }

    @Override
    public void offline(Long userId, String deviceId) {
        String key = USER_SET_PREFIX + userId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_OFFLINE, Long.class);
        Long onlineCount = redisTemplate.execute(script, Collections.singletonList(key), deviceId);

        log.info("设备离线: userId={}, deviceId={}, 剩余在线设备数={}", userId, deviceId, onlineCount);
    }

    @Override
    public void heartbeat(Long userId, String deviceId) {
        String userKey = USER_SET_PREFIX + userId;
        String devKey = DEV_INFO_PREFIX + userId + ":" + deviceId;
        long ttl = presenceConfig.getLeaseTtlSeconds();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_HEARTBEAT, Long.class);
        redisTemplate.execute(script, List.of(userKey, devKey), deviceId, String.valueOf(ttl));

        log.debug("续租: userId={}, deviceId={}, ttl={}s", userId, deviceId, ttl);
    }

    @Override
    public boolean isOnline(Long userId) {
        String key = USER_SET_PREFIX + userId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_IS_ONLINE, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key));
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public Map<Long, Boolean> batchIsOnline(List<Long> userIds) {
        Map<Long, Boolean> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        List<String> keys = userIds.stream().map(id -> USER_SET_PREFIX + id).toList();
        DefaultRedisScript<List> script = new DefaultRedisScript<>(LUA_BATCH_ONLINE, List.class);
        List<?> states = redisTemplate.execute(script, keys);
        for (int i = 0; i < userIds.size(); i++) {
            Object state = states != null && i < states.size() ? states.get(i) : 0;
            result.put(userIds.get(i), "1".equals(String.valueOf(state)));
        }
        return result;
    }

    @Override
    public String getNodeId(Long userId, String deviceId) {
        String devKey = DEV_INFO_PREFIX + userId + ":" + deviceId;
        Object nodeId = redisTemplate.opsForHash().get(devKey, "nodeId");
        return nodeId != null ? nodeId.toString() : null;
    }

    @Override
    public Map<Long, Set<String>> getOnlineNodeIds(Collection<Long> userIds) {
        Map<Long, Set<String>> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<Long> orderedUsers = userIds.stream().distinct().toList();
        List<String> keys = orderedUsers.stream().map(id -> USER_SET_PREFIX + id).toList();
        Object[] args = orderedUsers.stream().map(String::valueOf).toArray();
        DefaultRedisScript<List> script = new DefaultRedisScript<>(LUA_BATCH_NODES, List.class);
        List<?> routes = redisTemplate.execute(script, keys, args);
        if (routes == null) {
            return result;
        }
        for (int i = 0; i + 1 < routes.size(); i += 2) {
            Long userId = Long.valueOf(String.valueOf(routes.get(i)));
            String targetNode = String.valueOf(routes.get(i + 1));
            result.computeIfAbsent(userId, ignored -> new HashSet<>()).add(targetNode);
        }
        return result;
    }

    /**
     * 获取用户所有在线设备 ID 列表
     * 供跨节点推送路由使用（当前单实例暂未用到，预留接口）
     */
    public List<String> getOnlineDevices(Long userId) {
        String key = USER_SET_PREFIX + userId;
        DefaultRedisScript<List> script = new DefaultRedisScript<>(LUA_LIST_DEVICES, List.class);
        List<?> devices = redisTemplate.execute(script, Collections.singletonList(key));
        if (devices == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object d : devices) {
            result.add(d.toString());
        }
        return result;
    }
}
