package com.ltcpond.msgrelay.im.strategy.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.im.model.dto.SendMessageRequest;
import com.ltcpond.msgrelay.im.strategy.MessageHandlerStrategy;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 语音消息处理 — 校验语音 URL 和时长 */
@Component
public class VoiceMessageHandler implements MessageHandlerStrategy {

    private static final int MAX_DURATION = 60;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Integer getType() {
        return 4;
    }

    @Override
    @SneakyThrows
    public String process(Long senderId, SendMessageRequest request) {
        String content = request.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        content = content.trim();

        // 校验语音时长元数据
        if (request.getMediaMetaJson() != null && !request.getMediaMetaJson().isBlank()) {
            Map<String, Object> meta = objectMapper.readValue(request.getMediaMetaJson(), Map.class);
            Object durationObj = meta.get("duration");
            if (durationObj instanceof Number duration) {
                if (duration.intValue() > MAX_DURATION) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "语音时长超出限制，最长60秒");
                }
            }
        }

        return content;
    }
}
