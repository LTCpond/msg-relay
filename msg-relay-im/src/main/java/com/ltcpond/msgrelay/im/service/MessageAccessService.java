package com.ltcpond.msgrelay.im.service;

import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.group.repository.GroupMemberMapper;
import com.ltcpond.msgrelay.im.model.entity.ChatConversation;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import com.ltcpond.msgrelay.im.repository.ChatConversationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** 历史、搜索、ACK 和已读状态共用的 conversationId 授权入口。 */
@Service
public class MessageAccessService {

    @Resource
    private ChatConversationMapper conversationMapper;

    @Resource
    private MultiLevelCache cache;

    @Resource
    private GroupMemberMapper groupMemberMapper;

    public ChatConversation getConversation(Long conversationId) {
        if (conversationId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "conversationId 不能为空");
        }
        ChatConversation conversation = cache.get("conversation:" + conversationId,
                ChatConversation.class, key -> conversationMapper.selectById(conversationId), 1800);
        if (conversation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    public ChatConversation assertCanAccessConversation(Long userId, Long conversationId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该会话");
        }
        ChatConversation conversation = getConversation(conversationId);
        if (conversation.getType() == ReceiverType.GROUP.getCode()) {
            if (groupMemberMapper.selectByGroupIdAndUserId(conversation.getGroupId(), userId) == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "你不是该群成员");
            }
        } else if (conversation.getType() == ReceiverType.SINGLE.getCode()) {
            if (!conversation.isParticipant(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该会话");
            }
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的会话类型");
        }
        return conversation;
    }

    public ChatConversation assertCanAccessMessage(Long userId, Message message) {
        if (message == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "消息不存在");
        }
        return assertCanAccessConversation(userId, message.getConversationId());
    }

    public ChatConversation assertCanAcknowledge(Long userId, Message message) {
        ChatConversation conversation = assertCanAccessMessage(userId, message);
        if (conversation.getType() == ReceiverType.SINGLE.getCode()
                && userId.equals(message.getSenderId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有消息接收者可以确认 ACK");
        }
        if (conversation.getType() == ReceiverType.GROUP.getCode()
                && userId.equals(message.getSenderId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "发送者不能确认自己的群消息");
        }
        return conversation;
    }
}
