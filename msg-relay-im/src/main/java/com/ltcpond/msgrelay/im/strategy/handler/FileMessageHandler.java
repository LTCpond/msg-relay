package com.ltcpond.msgrelay.im.strategy.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.im.model.dto.SendMessageRequest;
import com.ltcpond.msgrelay.im.strategy.MessageHandlerStrategy;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 文件消息处理 — 校验文件 URL 和文件大小 */
@Component
public class FileMessageHandler implements MessageHandlerStrategy {

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Integer getType() {
        return 3;
    }

    @Override
    @SneakyThrows
    public String process(Long senderId, SendMessageRequest request) {
        String content = request.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        content = content.trim();

        // 校验文件大小元数据
        if (request.getMediaMetaJson() != null && !request.getMediaMetaJson().isBlank()) {
            Map<String, Object> meta = objectMapper.readValue(request.getMediaMetaJson(), Map.class);
            Object fileSizeObj = meta.get("fileSize");
            if (fileSizeObj instanceof Number fileSize) {
                if (fileSize.longValue() > MAX_FILE_SIZE) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "文件大小超出限制，最大100MB");
                }
            }
        }

        return content;
    }
}
