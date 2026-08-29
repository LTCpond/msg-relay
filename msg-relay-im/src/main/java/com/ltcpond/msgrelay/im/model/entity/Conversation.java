package com.ltcpond.msgrelay.im.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 会话实体 — 用户与单聊/群聊之间的一对一会话记录 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Conversation extends BaseEntity {

    /** 会话所属用户 ID */
    private Long userId;
    /** 对方 ID（单聊为用户 ID，群聊为群 ID） */
    private Long targetId;
    /** 对方类型: 1=单聊 2=群聊 */
    private Integer targetType;
    /** 最后一条消息 ID */
    private Long lastMsgId;
    /** 未读计数 */
    private Integer unreadCount;
    /** 上次阅读时间 */
    private LocalDateTime lastReadTime;
}
