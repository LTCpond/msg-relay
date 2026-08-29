package com.ltcpond.msgrelay.im.strategy.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.im.model.dto.SendMessageRequest;
import com.ltcpond.msgrelay.im.strategy.MessageHandlerStrategy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 图片消息处理 — 校验 URL 格式和图片尺寸 */
@Slf4j
@Component
public class ImageMessageHandler implements MessageHandlerStrategy {

    private static final int MAX_WIDTH = 10000;
    private static final int MAX_HEIGHT = 10000;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Integer getType() {
        return 2;
    }

    @Override
    @SneakyThrows
    public String process(Long senderId, SendMessageRequest request) {
        String content = request.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        content = content.trim();
        if (!content.startsWith("http://") && !content.startsWith("https://")) {
            log.warn("Image URL appears invalid: {}", content);
        }

        // 校验图片尺寸元数据
        if (request.getMediaMetaJson() != null && !request.getMediaMetaJson().isBlank()) {
            Map<String, Object> meta = objectMapper.readValue(request.getMediaMetaJson(), Map.class);
            Object widthObj = meta.get("width");
            Object heightObj = meta.get("height");
            if (widthObj instanceof Number width && heightObj instanceof Number height) {
                if (width.intValue() > MAX_WIDTH || height.intValue() > MAX_HEIGHT) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "图片尺寸超出限制");
                }
            }
        }

        return content;
    }
}
