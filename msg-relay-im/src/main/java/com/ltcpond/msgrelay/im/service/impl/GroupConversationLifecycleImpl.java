package com.ltcpond.msgrelay.im.service.impl;

import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import com.ltcpond.msgrelay.group.service.GroupConversationLifecycle;
import com.ltcpond.msgrelay.im.model.entity.ChatConversation;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import com.ltcpond.msgrelay.im.repository.ChatConversationMapper;
import com.ltcpond.msgrelay.im.repository.ConversationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** 将群成员生命周期同步到全局会话及用户会话投影。 */
@Service
public class GroupConversationLifecycleImpl implements GroupConversationLifecycle {

    @Resource
    private ChatConversationMapper chatConversationMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private SnowflakeIdGenerator idGenerator;

    @Resource
    private MultiLevelCache cache;

    @Override
    public void create(Long conversationId, Long groupId, Long ownerId) {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(conversationId);
        conversation.setType(ReceiverType.GROUP.getCode());
        conversation.setGroupId(groupId);
        conversation.setDeleted(0);
        chatConversationMapper.insert(conversation);
        cache.evict("conversation:" + conversationId);
        addMember(conversationId, ownerId);
    }

    @Override
    public void addMember(Long conversationId, Long userId) {
        conversationMapper.upsertMember(idGenerator.nextId(), userId, conversationId);
        cache.evict("conv:list:" + userId);
    }

    @Override
    public void removeMember(Long conversationId, Long userId) {
        conversationMapper.deleteByUserAndConversation(userId, conversationId);
        cache.evict("conv:list:" + userId);
    }

    @Override
    public void delete(Long conversationId) {
        java.util.List<Long> userIds = conversationMapper.selectUserIdsByConversationId(conversationId);
        conversationMapper.deleteByConversationId(conversationId);
        chatConversationMapper.deleteByIdLogic(conversationId);
        cache.evict("conversation:" + conversationId);
        userIds.forEach(userId -> cache.evict("conv:list:" + userId));
    }
}
