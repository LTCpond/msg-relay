package com.ltcpond.msgrelay.im.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 全局会话主体：一场单聊或群聊只对应一个 conversationId。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatConversation extends BaseEntity {

    /** 1=单聊 2=群聊 */
    private Integer type;
    /** 单聊双方中较小的用户 ID */
    private Long directUserLow;
    /** 单聊双方中较大的用户 ID */
    private Long directUserHigh;
    /** 群聊对应的群 ID */
    private Long groupId;

    public boolean isParticipant(Long userId) {
        return userId != null && (userId.equals(directUserLow) || userId.equals(directUserHigh));
    }

    public Long peerOf(Long userId) {
        if (userId != null && userId.equals(directUserLow)) return directUserHigh;
        if (userId != null && userId.equals(directUserHigh)) return directUserLow;
        return null;
    }
}
