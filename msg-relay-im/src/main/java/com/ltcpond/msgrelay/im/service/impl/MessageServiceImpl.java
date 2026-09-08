package com.ltcpond.msgrelay.im.service.impl;

import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import com.ltcpond.msgrelay.common.lock.LockTemplate;
import com.ltcpond.msgrelay.im.builder.MessageBuilder;
import com.ltcpond.msgrelay.im.model.dto.SendMessageRequest;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.entity.UserMessageHide;
import com.ltcpond.msgrelay.im.model.enums.MessageStatus;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import com.ltcpond.msgrelay.im.model.vo.MessageVO;
import com.ltcpond.msgrelay.im.model.vo.ReadStatusVO;
import com.ltcpond.msgrelay.im.mq.MessageProducer;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.group.service.BitmapAckService;
import com.ltcpond.msgrelay.group.service.GroupService;
import com.ltcpond.msgrelay.im.repository.MessageMapper;
import com.ltcpond.msgrelay.im.netty.NodePushRouter;
import com.ltcpond.msgrelay.im.repository.UserMessageHideMapper;
import com.ltcpond.msgrelay.im.service.MessageService;
import com.ltcpond.msgrelay.im.service.MessageAccessService;
import com.ltcpond.msgrelay.im.strategy.MessageStrategyFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消息服务 — IM 核心，处理消息发送/撤回/查询
 *
 * 消息发送流程（异步架构）:
 * 1. 雪花算法生成 msgId
 * 2. 先落库 MySQL（确保消息不丢失）
 * 3. 投递 RocketMQ（异步分发 + 推送）
 * 4. Consumer 消费: 更新会话 + 观察者推送在线用户
 *
 * 热点缓存:
 * - getByMsgId: ACK/MQ 消费时高频单条查消息，Caffeine L1 命中后跳过 Redis 和 DB
 *
 * 消息状态机: SENDING(0) → SENT(1) → DELIVERED(2) → READ(3)
 * 群聊同样使用投递状态机，消息类别由 receiverType 区分；成员 ACK 使用 Bitmap。
 * 撤回: 任意状态 → RECALLED(4)
 */
@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private MessageProducer messageProducer;

    @Resource
    private SnowflakeIdGenerator idGenerator;

    @Resource
    private MultiLevelCache cache;

    @Resource
    private UserMessageHideMapper hideMapper;

    @Resource
    private NodePushRouter pushRouter;

    @Resource
    private GroupService groupService;

    @Resource
    private UserService userService;

    @Resource
    private MessageStrategyFactory strategyFactory;

    @Resource
    private BitmapAckService bitmapAckService;

    @Resource
    private MessageAccessService messageAccessService;

    @Resource
    private LockTemplate lockTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long MSG_CACHE_TTL = 600;

    /** 发送消息 — 策略处理 → 建造者构造 → 事务落库 → MQ 投递，立即返回 VO */
    @Override
    public MessageVO send(Long senderId, SendMessageRequest request) {
        messageAccessService.assertCanAccessConversation(senderId, request.getReceiverId(), request.getReceiverType());
        String lockKey = "msg:send:" + senderId + ":" + request.getClientMsgId();
        return lockTemplate.executeWithLock(lockKey, 3, 15, () -> sendIdempotently(senderId, request));
    }

    private MessageVO sendIdempotently(Long senderId, SendMessageRequest request) {
        String processedContent = strategyFactory.getStrategy(request.getMsgType())
                .process(senderId, request);
        Message existing = messageMapper.selectBySenderAndClientMsgId(senderId, request.getClientMsgId());
        if (existing != null) {
            if (!existing.getReceiverId().equals(request.getReceiverId())
                    || !existing.getReceiverType().equals(request.getReceiverType())
                    || !existing.getMsgType().equals(request.getMsgType())
                    || !java.util.Objects.equals(existing.getContent(), processedContent)
                    || !java.util.Objects.equals(existing.getExtraJson(), request.getExtraJson())
                    || !java.util.Objects.equals(existing.getMediaMetaJson(), request.getMediaMetaJson())) {
                throw new BusinessException(ResultCode.CONFLICT, "clientMsgId 已被另一条消息使用");
            }
            return toVO(existing);
        }
        // 2. 建造者模式：构造消息体
        Message message = MessageBuilder.builder()
                .sender(senderId)
                .receiver(request.getReceiverId(), request.getReceiverType())
                .msgType(request.getMsgType())
                .content(processedContent)
                .extraJson(request.getExtraJson())
                .mediaMetaJson(request.getMediaMetaJson())
                .build();
        message.setId(idGenerator.nextId());
        message.setMsgId(idGenerator.nextId());
        message.setClientMsgId(request.getClientMsgId());
        message.setDeleted(0);
        // 发送请求初始为 SENDING；RocketMQ 本地事务落库时改为 SENT。
        message.setStatus(MessageStatus.SENDING.getCode());
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        // 1. 使用 RocketMQ 事务消息，内部完成落库与投递
        TransactionSendResult sendResult = messageProducer.sendMessage(message);
        log.info("Message send accepted: msgId={}, mqMsgId={}",
                message.getMsgId(), sendResult.getMsgId());
        return toVO(message);
    }

    /** 撤回消息 — 校验权限和 2 小时时限，更新状态后推送撤回事件 */
    @Override
    public void recall(Long userId, Long msgId) {
        Message message = messageMapper.selectByMsgId(msgId);
        if (message == null || !message.getSenderId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权撤回该消息");
        }
        if (message.getCreatedAt().plusHours(2).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.MSG_RECALL_TIMEOUT);
        }
        message.setStatus(MessageStatus.RECALLED.getCode());
        message.setDeleted(1);
        message.setUpdatedAt(LocalDateTime.now());
        messageMapper.updateById(message);
        cache.evict("msg:" + msgId);
        pushRecall(message);
    }

    /** 根据 msgId 查消息 — 三级缓存，ACK/MQ 消费高频调用 */
    @Override
    public MessageVO getByMsgId(Long msgId) {
        // ACK 回调、MQ 消费时高频查单条消息 — Caffeine L1 命中率极高
        Message message = cache.get("msg:" + msgId, Message.class,
                key -> messageMapper.selectByMsgId(msgId), MSG_CACHE_TTL);
        return message != null ? toVO(message) : null;
    }

    /** 根据 msgId 查消息（返回实体） */
    @Override
    public Message getByMsgIdEntity(Long msgId) {
        Message message = cache.get("msg:" + msgId, Message.class,
                key -> messageMapper.selectByMsgId(msgId), MSG_CACHE_TTL);
        return message;
    }

    /** 标记单聊消息为已投递 */
    @Override
    public void markDelivered(Long msgId) {
        if (messageMapper.advanceStatus(msgId, MessageStatus.DELIVERED.getCode()) > 0) {
            cache.evict("msg:" + msgId);
        }
    }

    /** 标记单聊消息为已读 */
    @Override
    public void markRead(Long msgId) {
        if (messageMapper.advanceStatus(msgId, MessageStatus.READ.getCode()) > 0) {
            cache.evict("msg:" + msgId);
        }
    }

    /** 查询历史消息 — 分页拉取，自动过滤用户隐藏的消息，支持单聊和群聊 */
    @Override
    public List<MessageVO> queryHistory(Long userId, Long targetId, Integer receiverType, Long beforeMsgId, int limit) {
        messageAccessService.assertCanAccessConversation(userId, targetId, receiverType);
        List<Message> messages = messageMapper.selectHistory(userId, targetId, receiverType, beforeMsgId, limit);
        Set<String> hiddenSet = getHiddenSet(userId);
        return messages.stream()
                .filter(m -> !hiddenSet.contains(m.getMsgId().toString()))
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /** 隐藏消息 — 写入隐藏记录，清缓存，推送 hide 事件 */
    @Override
    public void hideMessage(Long userId, Long msgId) {
        messageAccessService.assertCanAccessMessage(userId, messageMapper.selectByMsgId(msgId));
        UserMessageHide hide = new UserMessageHide();
        hide.setId(idGenerator.nextId());
        hide.setUserId(userId);
        hide.setMsgId(msgId);
        hide.setCreatedAt(LocalDateTime.now());
        hideMapper.insert(hide);
        cache.evict("user:hidden:msgs:" + userId);
        pushHide(userId, msgId);
    }

    /** 推送撤回通知给所有会话成员 */
    private void pushRecall(Message message) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "recall",
                    "msgId", message.getMsgId()
            ));
            if (message.getReceiverType() == ReceiverType.GROUP.getCode()) {
                Set<Long> memberIds = groupService.getMemberIds(message.getReceiverId()).stream()
                        .map(Long::valueOf).collect(Collectors.toSet());
                pushRouter.pushToUsers(memberIds, json);
            } else {
                pushRouter.pushToUsers(Set.of(message.getSenderId(), message.getReceiverId()), json);
            }
        } catch (Exception e) {
            log.error("Failed to push recall event: msgId={}", message.getMsgId(), e);
        }
    }

    /** 推送 hide 事件到用户所有在线设备 */
    private void pushHide(Long userId, Long msgId) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "hide",
                    "msgId", msgId
            ));
            pushRouter.pushToUser(userId, json);
        } catch (Exception e) {
            log.error("Failed to push hide event: userId={}, msgId={}", userId, msgId, e);
        }
    }

    /** 用户隐藏消息集合 — 三级缓存，Redis 未命中时回源 DB */
    @SuppressWarnings("unchecked")
    private Set<String> getHiddenSet(Long userId) {
        String key = "user:hidden:msgs:" + userId;
        Set<String> result = cache.get(key, Set.class,
                k -> {
                    List<Long> msgIds = hideMapper.selectMsgIdsByUserId(userId);
                    return msgIds.stream().map(String::valueOf).collect(Collectors.toSet());
                }, 300);
        return result != null ? result : Collections.emptySet();
    }

    /** Entity → VO 转换 */
    private MessageVO toVO(Message msg) {
        MessageVO vo = new MessageVO();
        vo.setId(msg.getId());
        vo.setMsgId(msg.getMsgId());
        vo.setClientMsgId(msg.getClientMsgId());
        vo.setSenderId(msg.getSenderId());
        vo.setSenderName(getSenderName(msg.getSenderId()));
        vo.setReceiverId(msg.getReceiverId());
        vo.setReceiverType(msg.getReceiverType());
        vo.setMsgType(msg.getMsgType());
        vo.setContent(msg.getContent());
        vo.setExtraJson(msg.getExtraJson());
        vo.setMediaMetaJson(msg.getMediaMetaJson());
        vo.setStatus(msg.getStatus());
        vo.setCreatedAt(msg.getCreatedAt());
        return vo;
    }

    private String getSenderName(Long senderId) {
        try {
            return userService.getById(senderId).getNickname();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 查询消息已读状态
     *
     * 逻辑:
     * 1. 根据 msgId 查询消息，不存在则抛异常
     * 2. 单聊: 根据消息 status 判断已读/未读
     * 3. 群聊: 使用 Bitmap 查询已读/未读用户列表
     *
     * @param msgId 消息 ID
     * @return 已读状态 VO（已读人数、已读列表、未读列表）
     */
    @Override
    public ReadStatusVO getReadStatus(Long userId, Long msgId) {
        Message message = messageMapper.selectByMsgId(msgId);
        messageAccessService.assertCanAccessMessage(userId, message);

        ReadStatusVO vo = new ReadStatusVO();
        vo.setMsgId(msgId);

        if (message.getReceiverType() != null && message.getReceiverType() == 2) {
            // 群聊：使用 Bitmap 查询
            Long groupId = message.getReceiverId();

            // 已读用户列表
            List<Long> readUserIds = bitmapAckService.getReadUsers(msgId, groupId);
            List<ReadStatusVO.ReadUser> readList = readUserIds.stream()
                    .map(readUserId -> {
                        String userName = userService.getById(readUserId).getNickname();
                        return new ReadStatusVO.ReadUser(readUserId, userName, null);
                    })
                    .collect(Collectors.toList());
            vo.setReadList(readList);
            vo.setReadCount(readUserIds.size());

            // 未读用户列表（排除发送者）
            List<Long> unreadUserIds = bitmapAckService.getUnreadUsers(msgId, groupId);
            List<ReadStatusVO.ReadUser> unreadList = unreadUserIds.stream()
                    .filter(unreadUserId -> !unreadUserId.equals(message.getSenderId()))
                    .map(unreadUserId -> {
                        String userName = userService.getById(unreadUserId).getNickname();
                        return new ReadStatusVO.ReadUser(unreadUserId, userName, null);
                    })
                    .collect(Collectors.toList());
            vo.setUnreadList(unreadList);
        } else {
            // 单聊：根据消息 status 判断
            boolean isRead = message.getStatus() != null
                    && message.getStatus() == MessageStatus.READ.getCode();
            vo.setReadCount(isRead ? 1 : 0);
            vo.setReadList(Collections.emptyList());
            vo.setUnreadList(Collections.emptyList());
        }

        return vo;
    }
}
