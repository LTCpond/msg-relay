package com.ltcpond.msgrelay.im.observer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.common.constants.RedisChannel;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import com.ltcpond.msgrelay.im.netty.SessionManager;
import com.ltcpond.msgrelay.group.service.GroupService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * WebSocket 推送观察者 — 消息通过 WebSocket 实时推送到在线用户
 *
 * 推送策略:
 * - 单聊: 推给接收者 + 推给发送方其他设备（多端同步）
 * - 小群: 扩散写（遍历成员逐个推送）+ 推给发送方其他设备
 * - 大群: 仅推送轻量级通知（客户端拉取完整消息）+ 推给发送方其他设备
 */
@Slf4j
@Component
public class WebSocketPushObserver implements MessagePushObserver {

    @Resource
    private SessionManager sessionManager;

    @Resource
    private GroupService groupService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> kickRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(Message message) {
        if (ReceiverType.GROUP.getCode() == message.getReceiverType()) {
            handleGroupMessage(message);
        } else {
            handleSingleMessage(message, message.getReceiverId());
        }
        // 多端同步：推送给发送方的其他设备
        pushToSenderOtherDevices(message);
    }

    /** 单聊推送 — 序列化完整消息推给目标用户 */
    private void handleSingleMessage(Message message, Long targetUserId) {
        try {
            String json = buildMessageJson(message);
            pushToUser(targetUserId, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message", e);
        }
    }

    /** 群聊推送 — 大群发通知（拉模式），小群扩散写（推模式） */
    private void handleGroupMessage(Message message) {
        Long groupId = message.getReceiverId();
        Set<String> memberIds = groupService.getMemberIds(groupId);

        if (groupService.isLargeGroup(groupId)) {
            pushGroupNotify(groupId, memberIds, message.getSenderId());
        } else {
            for (String memberIdStr : memberIds) {
                Long targetUserId = Long.valueOf(memberIdStr);
                if (targetUserId.equals(message.getSenderId())) continue;
                handleSingleMessage(message, targetUserId);
            }
        }
    }

    /** 大群轻量级通知 — 客户端收到通知后主动拉取消息 */
    private void pushGroupNotify(Long groupId, Set<String> memberIds, Long senderId) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "group_notify",
                    "groupId", groupId
            ));
            for (String memberIdStr : memberIds) {
                Long targetUserId = Long.valueOf(memberIdStr);
                if (targetUserId.equals(senderId)) continue;
                pushToUser(targetUserId, json);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize group notify", e);
        }
    }

    /** 多端同步 — 推送给发送方的其他设备 */
    private void pushToSenderOtherDevices(Message message) {
        try {
            String json = buildMessageJson(message);
            pushToUser(message.getSenderId(), json);
        } catch (JsonProcessingException e) {
            log.error("Failed to push to sender other devices", e);
        }
    }

    /** 通过 Redis Pub/Sub 发布推送指令，所有节点订阅后各自本地投递 */
    private void pushToUser(Long userId, String json) {
        if (kickRedisTemplate != null) {
            kickRedisTemplate.convertAndSend(RedisChannel.PUSH_CHANNEL, userId + "|" + json);
        } else {
            sessionManager.pushToUser(userId, json);
        }
    }

    /** 构建消息 JSON */
    private String buildMessageJson(Message message) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "type", "message",
                "msgId", message.getMsgId(),
                "senderId", message.getSenderId(),
                "content", message.getContent(),
                "msgType", message.getMsgType(),
                "extraJson", message.getExtraJson() != null ? message.getExtraJson() : "",
                "mediaMetaJson", message.getMediaMetaJson() != null ? message.getMediaMetaJson() : ""
        ));
    }
}
