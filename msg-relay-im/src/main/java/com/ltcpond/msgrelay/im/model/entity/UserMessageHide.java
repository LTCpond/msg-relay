package com.ltcpond.msgrelay.im.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户消息隐藏记录 — 删除消息只对自己不可见 */
@Data
public class UserMessageHide {

    private Long id;
    private Long userId;
    private Long msgId;
    private LocalDateTime createdAt;
}
