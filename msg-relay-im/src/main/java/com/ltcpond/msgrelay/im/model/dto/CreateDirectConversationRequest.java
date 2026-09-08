package com.ltcpond.msgrelay.im.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 创建或获取单聊会话。 */
@Data
public class CreateDirectConversationRequest {

    @NotNull(message = "targetUserId 不能为空")
    private Long targetUserId;
}
