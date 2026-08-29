package com.ltcpond.msgrelay.im.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.enums.MessageStatus;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息建造者 — 流式 API 构造复杂消息体
 *
 * 消息字段说明:
 * - extraJson: 通用扩展（引用消息、@提醒等）
 * - mediaMetaJson: 媒体元数据（图片宽高、语音时长、文件名/大小）
 */
public class MessageBuilder {

    private final Message message;
    private final Map<String, Object> extra;

    public MessageBuilder() {
        this.message = new Message();
        this.extra = new HashMap<>();
    }

    public static MessageBuilder builder() {
        return new MessageBuilder();
    }

    public MessageBuilder sender(Long senderId) {
        message.setSenderId(senderId);
        return this;
    }

    public MessageBuilder receiver(Long receiverId, Integer receiverType) {
        message.setReceiverId(receiverId);
        message.setReceiverType(receiverType);
        return this;
    }

    public MessageBuilder msgType(Integer msgType) {
        message.setMsgType(msgType);
        return this;
    }

    public MessageBuilder content(String content) {
        message.setContent(content);
        return this;
    }

    public MessageBuilder quoteMsg(Long quotedMsgId) {
        extra.put("quoteMsgId", quotedMsgId);
        return this;
    }

    public MessageBuilder atUsers(String[] userIds) {
        extra.put("atUsers", userIds);
        return this;
    }

    public MessageBuilder extraJson(String extraJson) {
        message.setExtraJson(extraJson);
        return this;
    }

    public MessageBuilder mediaMetaJson(String mediaMetaJson) {
        message.setMediaMetaJson(mediaMetaJson);
        return this;
    }

    @SneakyThrows
    public Message build() {
        if (message.getMsgId() == null) {
            message.setMsgId(System.currentTimeMillis());
        }
        message.setStatus(MessageStatus.SENDING.getCode());
        if (!extra.isEmpty()) {
            if (message.getExtraJson() != null) {
                // 如果外部已经传入了 extraJson，优先反序列化合并
                Map<String, Object> existingExtra = new ObjectMapper().readValue(message.getExtraJson(), Map.class);
                existingExtra.putAll(extra);
                message.setExtraJson(new ObjectMapper().writeValueAsString(existingExtra));
            } else {
                message.setExtraJson(new ObjectMapper().writeValueAsString(extra));
            }
        }
        return message;
    }
}
