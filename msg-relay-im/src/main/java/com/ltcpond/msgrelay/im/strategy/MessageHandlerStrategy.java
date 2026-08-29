package com.ltcpond.msgrelay.im.strategy;

import com.ltcpond.msgrelay.im.model.dto.SendMessageRequest;

/** 消息处理策略接口 — 策略模式，按消息类型分发处理 */
public interface MessageHandlerStrategy {

    /** 返回处理的 MessageType code */
    Integer getType();

    /** 处理消息内容并返回处理后的 content */
    String process(Long senderId, SendMessageRequest request);
}
