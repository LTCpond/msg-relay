package com.ltcpond.msgrelay.im.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 消息状态枚举 — 状态机: SENDING → SENT → DELIVERED → READ，任意状态可 → RECALLED，群聊 → GROUP */
@Getter
@AllArgsConstructor
public enum MessageStatus {

    SENDING(0, "发送中"),
    SENT(1, "已发送"),
    DELIVERED(2, "已投递"),
    READ(3, "已读"),
    RECALLED(4, "已撤回"),
    GROUP(5, "群聊");  // 群聊状态，不参与三阶段判断，用 Bitmap 记录投递/已读

    private final int code;
    private final String desc;
}
