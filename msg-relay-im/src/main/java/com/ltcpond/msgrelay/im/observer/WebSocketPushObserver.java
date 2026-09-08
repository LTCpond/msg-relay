package com.ltcpond.msgrelay.im.observer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import com.ltcpond.msgrelay.im.netty.NodePushRouter;
import com.ltcpond.msgrelay.group.service.GroupService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

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
    private NodePushRouter pushRouter;

    @Resource
    private GroupService groupService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(Message message) {
        if (ReceiverType.GROUP.getCode() == message.getConversationType()) {
            handleGroupMessage(message);
        } else {
            try {
                pushRouter.pushToUsers(Set.of(message.getConversationTargetId(), message.getSenderId()),
                        buildMessageJson(message));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize message", e);
            }
        }
    }

    /** 群聊推送 — 大群发通知（拉模式），小群扩散写（推模式） */
    private void handleGroupMessage(Message message) {
        Long groupId = message.getConversationTargetId();
        Set<String> memberIds = groupService.getMemberIds(groupId);

        if (groupService.isLargeGroup(groupId)) {
            pushGroupNotify(message, memberIds);
        } else {
            try {
                Set<Long> targets = memberIds.stream().map(Long::valueOf).collect(Collectors.toSet());
                pushRouter.pushToUsers(targets, buildMessageJson(message));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize group message", e);
            }
        }
    }

    /** 大群轻量级通知 — 客户端收到通知后主动拉取消息 */
    private void pushGroupNotify(Message message, Set<String> memberIds) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "group_notify",
                    "conversationId", message.getConversationId(),
                    "lastMsgId", message.getMsgId()
            ));
            Set<Long> recipients = memberIds.stream().map(Long::valueOf)
                    .filter(userId -> !userId.equals(message.getSenderId())).collect(Collectors.toSet());
            pushRouter.pushToUsers(recipients, json);
            pushRouter.pushToUser(message.getSenderId(), buildMessageJson(message));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize group notify", e);
        }
    }

    /** 构建消息 JSON */
    private String buildMessageJson(Message message) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "type", "message",
                "msgId", message.getMsgId(),
                "conversationId", message.getConversationId(),
                "senderId", message.getSenderId(),
                "content", message.getContent(),
                "msgType", message.getMsgType(),
                "extraJson", message.getExtraJson() != null ? message.getExtraJson() : "",
                "mediaMetaJson", message.getMediaMetaJson() != null ? message.getMediaMetaJson() : ""
        ));
    }
}
