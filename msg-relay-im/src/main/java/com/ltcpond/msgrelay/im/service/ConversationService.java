package com.ltcpond.msgrelay.im.service;

import com.ltcpond.msgrelay.im.model.entity.Conversation;

import java.util.List;

/** 会话服务接口 */
public interface ConversationService {

    /** 创建或获取两个用户之间的唯一单聊会话。 */
    Conversation createDirectConversation(Long userId, Long targetUserId);

    /** 更新会话 — 新消息到达时创建或更新会话记录 */
    void updateConversation(Long userId, Long conversationId, Long msgId,
                            boolean incrementUnread);

    /** 标记已读 — 清零未读计数 */
    void markRead(Long userId, Long conversationId);

    /** 获取用户会话列表 */
    List<Conversation> listConversations(Long userId);

    /** 查询指定会话 */
    Conversation getConversation(Long userId, Long conversationId);
}
