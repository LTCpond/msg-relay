package com.ltcpond.msgrelay.im.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 接收者类型枚举 — 区分单聊和群聊 */
@Getter
@AllArgsConstructor
public enum ReceiverType {

    SINGLE(1, "单聊"),
    GROUP(2, "群聊");

    private final int code;
    private final String desc;
}
