package com.ltcpond.msgrelay.im.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 消息类型枚举 */
@Getter
@AllArgsConstructor
public enum MessageType {

    TEXT(1, "文本"),
    IMAGE(2, "图片"),
    FILE(3, "文件"),
    VOICE(4, "语音"),
    SYSTEM(5, "系统通知");

    private final int code;
    private final String desc;

    /** 根据 code 获取对应枚举，未匹配时默认返回 TEXT */
    public static MessageType fromCode(int code) {
        for (MessageType t : values()) {
            if (t.code == code) return t;
        }
        return TEXT;
    }
}
