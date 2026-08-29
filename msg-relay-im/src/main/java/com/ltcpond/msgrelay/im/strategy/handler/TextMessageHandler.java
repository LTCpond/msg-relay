package com.ltcpond.msgrelay.im.strategy.handler;

import com.ltcpond.msgrelay.im.model.dto.SendMessageRequest;
import com.ltcpond.msgrelay.im.strategy.MessageHandlerStrategy;
import org.springframework.stereotype.Component;

/** 文本消息处理 — trim + 超长截断 */
@Component
public class TextMessageHandler implements MessageHandlerStrategy {

    private static final int MAX_TEXT_LENGTH = 5000;

    @Override
    public Integer getType() {
        return 1;
    }

    @Override
    public String process(Long senderId, SendMessageRequest request) {
        String content = request.getContent();
        if (content == null) {
            return "";
        }
        content = content.trim();
        if (content.length() > MAX_TEXT_LENGTH) {
            content = content.substring(0, MAX_TEXT_LENGTH);
        }
        return content;
    }
}
