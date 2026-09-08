package com.ltcpond.msgrelay.im.service.impl;

import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import com.ltcpond.msgrelay.group.service.BitmapAckService;
import com.ltcpond.msgrelay.im.model.entity.ChatConversation;
import com.ltcpond.msgrelay.im.model.entity.Conversation;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import com.ltcpond.msgrelay.im.repository.ChatConversationMapper;
import com.ltcpond.msgrelay.im.repository.ConversationMapper;
import com.ltcpond.msgrelay.im.repository.MessageMapper;
import com.ltcpond.msgrelay.im.service.ConversationService;
import com.ltcpond.msgrelay.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 全局会话管理与用户会话列表投影。 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Resource
    private ChatConversationMapper chatConversationMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private SnowflakeIdGenerator idGenerator;

    @Resource
    private MultiLevelCache cache;

    @Resource
    private BitmapAckService bitmapAckService;

    @Resource
    private UserService userService;

    private static final long CONV_LIST_TTL = 300;

    @Override
    @Transactional
    public Conversation createDirectConversation(Long userId, Long targetUserId) {
        if (userId == null || targetUserId == null || userId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单聊会话参与者无效");
        }
        userService.getById(targetUserId);

        long low = Math.min(userId, targetUserId);
        long high = Math.max(userId, targetUserId);
        ChatConversation chat = chatConversationMapper.selectDirect(low, high);
        if (chat == null) {
            ChatConversation candidate = new ChatConversation();
            candidate.setId(idGenerator.nextId());
            candidate.setType(ReceiverType.SINGLE.getCode());
            candidate.setDirectUserLow(low);
            candidate.setDirectUserHigh(high);
            candidate.setDeleted(0);
            chatConversationMapper.insert(candidate);
            chat = chatConversationMapper.selectDirect(low, high);
            cache.evict("conversation:" + chat.getId());
        }

        conversationMapper.upsertMember(idGenerator.nextId(), userId, chat.getId());
        conversationMapper.upsertMember(idGenerator.nextId(), targetUserId, chat.getId());
        cache.evict("conv:list:" + userId);
        cache.evict("conv:list:" + targetUserId);
        return conversationMapper.selectByUserAndConversation(userId, chat.getId());
    }

    @Override
    public void updateConversation(Long userId, Long conversationId, Long msgId,
                                   boolean incrementUnread) {
        conversationMapper.upsertByMessage(idGenerator.nextId(), userId, conversationId,
                msgId, incrementUnread);
        cache.evict("conv:list:" + userId);
    }

    @Override
    public void markRead(Long userId, Long conversationId) {
        Conversation conv = conversationMapper.selectByUserAndConversation(userId, conversationId);
        if (conv == null) return;

        if (ReceiverType.GROUP.getCode() == conv.getType()) {
            LocalDateTime afterTime = conv.getLastReadTime() != null
                    ? conv.getLastReadTime()
                    : LocalDateTime.of(2000, 1, 1, 0, 0, 0);
            List<Message> messages = messageMapper.selectUnacknowledgedGroupMessages(
                    conversationId, userId, afterTime);
            for (Message msg : messages) {
                try {
                    bitmapAckService.markRead(msg.getMsgId(), userId, conv.getTargetId());
                } catch (Exception e) {
                    log.warn("Batch Bitmap ACK failed: userId={}, msgId={}", userId, msg.getMsgId(), e);
                }
            }
        }
        conv.setUnreadCount(0);
        conv.setLastReadTime(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        cache.evict("conv:list:" + userId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Conversation> listConversations(Long userId) {
        return cache.get("conv:list:" + userId, List.class,
                key -> conversationMapper.selectByUserId(userId), CONV_LIST_TTL);
    }

    @Override
    public Conversation getConversation(Long userId, Long conversationId) {
        return conversationMapper.selectByUserAndConversation(userId, conversationId);
    }
}
