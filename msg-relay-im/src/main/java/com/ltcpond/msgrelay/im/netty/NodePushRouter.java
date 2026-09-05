package com.ltcpond.msgrelay.im.netty;

import com.ltcpond.msgrelay.common.constants.RedisChannel;
import com.ltcpond.msgrelay.im.service.OnlineStatusService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 按 Presence 中的 device -> nodeId 聚合，仅向实际承载目标连接的节点发布。 */
@Component
public class NodePushRouter {

    @Resource
    private OnlineStatusService onlineStatusService;

    @Resource(name = "kickRedisTemplate")
    private RedisTemplate<String, Object> pubSubTemplate;

    @Resource
    private SessionManager sessionManager;

    @Value("${msg-relay.netty.node-id:node-1}")
    private String currentNodeId;

    public void pushToUser(Long userId, String payload) {
        pushToUsers(Set.of(userId), payload);
    }

    /** 同一 payload 在每个目标节点只发布一次，消息体携带该节点上的用户集合。 */
    public void pushToUsers(Collection<Long> userIds, String payload) {
        Set<Long> targets = userIds.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (targets.isEmpty()) {
            return;
        }

        Map<Long, Set<String>> routes = onlineStatusService.getOnlineNodeIds(targets);
        Map<String, Set<Long>> usersByNode = new LinkedHashMap<>();
        routes.forEach((userId, nodeIds) -> nodeIds.forEach(nodeId ->
                usersByNode.computeIfAbsent(nodeId, ignored -> new LinkedHashSet<>()).add(userId)));

        // AUTH 成功到 Presence 写入之间的极短窗口，仍允许本节点会话直接投递。
        for (Long userId : targets) {
            if (!routes.containsKey(userId) && !sessionManager.getChannelIds(userId).isEmpty()) {
                usersByNode.computeIfAbsent(currentNodeId, ignored -> new LinkedHashSet<>()).add(userId);
            }
        }

        usersByNode.forEach((nodeId, users) -> {
            String ids = users.stream().map(String::valueOf).collect(Collectors.joining(","));
            pubSubTemplate.convertAndSend(RedisChannel.pushChannel(nodeId), ids + "|" + payload);
        });
    }
}
