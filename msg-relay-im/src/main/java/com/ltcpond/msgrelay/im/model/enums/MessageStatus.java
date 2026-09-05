package com.ltcpond.msgrelay.im.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 投递状态枚举 — SENDING → SENT → DELIVERED → READ，任意状态可 → RECALLED。 */
@Getter
@AllArgsConstructor
public enum MessageStatus {

    SENDING(0, "发送中"),
    SENT(1, "已发送"),
    DELIVERED(2, "已投递"),
    READ(3, "已读"),
    RECALLED(4, "已撤回");

    private final int code;
    private final String desc;
}
