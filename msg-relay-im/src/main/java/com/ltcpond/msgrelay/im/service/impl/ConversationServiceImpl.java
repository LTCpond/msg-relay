package com.ltcpond.msgrelay.im.service.impl;

import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import com.ltcpond.msgrelay.im.model.entity.Conversation;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.repository.ConversationMapper;
import com.ltcpond.msgrelay.im.repository.MessageMapper;
import com.ltcpond.msgrelay.im.service.ConversationService;
import com.ltcpond.msgrelay.group.service.BitmapAckService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话服务 — 会话列表 + 已读管理
 *
 * 热点缓存: 会话列表是用户打开 APP 后的第一个请求，QPS 极高
 * 缓存策略: Cache Aside（先写 DB，再删缓存）
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

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

    private static final long CONV_LIST_TTL = 300; // 会话列表缓存 5 分钟

    /** 更新会话 — 新消息到达时若会话不存在则创建，否则原子递增未读计数 */
    @Override
    public void updateConversation(Long userId, Long targetId, Integer targetType, Long msgId,
                                   boolean incrementUnread) {
        conversationMapper.upsertByMessage(idGenerator.nextId(), userId, targetId, targetType,
                msgId, incrementUnread);
        cache.evict("conv:list:" + userId);
    }

    /** 标记已读 — 清零未读计数，群聊场景批量 ACK 未读消息（使用 Bitmap） */
    @Override
    public void markRead(Long userId, Long targetId, Integer targetType) {
        Conversation conv = conversationMapper.selectByUserAndTarget(userId, targetId, targetType);
        if (conv == null) {
            return;
        }
        // 群聊：批量标记 lastReadTime 之后的未读消息为已读
        if (conv.getTargetType() != null && conv.getTargetType() == 2) {
            LocalDateTime afterTime = conv.getLastReadTime() != null
                    ? conv.getLastReadTime()
                    : LocalDateTime.of(2000, 1, 1, 0, 0, 0);
            List<Message> messages = messageMapper.selectUnacknowledgedGroupMessages(
                    conv.getTargetId(), userId, afterTime);
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

    /** 会话列表 — 三级缓存（Caffeine → Redis → DB） */
    @Override
    @SuppressWarnings("unchecked")
    public List<Conversation> listConversations(Long userId) {
        // 热点: 用户打开 APP 必调，三级缓存加速
        return cache.get("conv:list:" + userId, List.class,
                key -> conversationMapper.selectByUserId(userId), CONV_LIST_TTL);
    }

    /** 查询指定会话 — 低频调用，直接走 DB */
    @Override
    public Conversation getConversation(Long userId, Long targetId, Integer targetType) {
        // 低频单条查询，直接走 DB
        return conversationMapper.selectByUserAndTarget(userId, targetId, targetType);
    }
}
