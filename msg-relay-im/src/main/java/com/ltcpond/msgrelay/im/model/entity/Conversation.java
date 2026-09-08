package com.ltcpond.msgrelay.im.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 用户会话投影 — 保存用户维度的未读数与最后阅读状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Conversation extends BaseEntity {

    /** 会话所属用户 ID */
    private Long userId;
    /** 全局会话 ID */
    private Long conversationId;
    /** 会话类型: 1=单聊 2=群聊（联表查询字段） */
    private Integer type;
    /** 展示目标：单聊为对方用户 ID，群聊为群 ID（联表查询字段） */
    private Long targetId;
    /** 最后一条消息 ID */
    private Long lastMsgId;
    /** 未读计数 */
    private Integer unreadCount;
    /** 上次阅读时间 */
    private LocalDateTime lastReadTime;
}
