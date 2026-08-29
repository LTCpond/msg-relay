package com.ltcpond.msgrelay.im.strategy.handler;

import com.ltcpond.msgrelay.im.model.dto.SendMessageRequest;
import com.ltcpond.msgrelay.im.strategy.MessageHandlerStrategy;
import org.springframework.stereotype.Component;

/** 系统消息处理 — 透传内容（加入群聊、移除成员等），前后端约定内容格式 */
@Component
public class SystemMessageHandler implements MessageHandlerStrategy {

    @Override
    public Integer getType() {
        return 5;
    }

    @Override
    public String process(Long senderId, SendMessageRequest request) {
        String content = request.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.trim();
    }
}
