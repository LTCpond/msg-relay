package com.ltcpond.msgrelay.im.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.observer.MessageObserverManager;
import com.ltcpond.msgrelay.im.repository.MessageMapper;
import com.ltcpond.msgrelay.im.service.ConversationService;
import com.ltcpond.msgrelay.group.service.GroupService;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import com.ltcpond.msgrelay.im.model.enums.MessageStatus;
import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.common.lock.LockTemplate;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 消息消费者 — 从 RocketMQ 消费消息，执行异步处理
 *
 * 消费链路:
 * 1. 反序列化消息体
 * 2. Redis hasKey 幂等校验（key 在业务成功后才 SET）
 * 3. 更新双方会话（原子 SQL：last_msg_id < msgId 防重复计数）
 * 4. 通知观察者推送在线用户
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "msg-relay-message-topic", consumerGroup = "msg-relay-message-consumer")
public class MessageConsumer implements RocketMQListener<String> {

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private ConversationService conversationService;

    @Resource
    private MessageObserverManager observerManager;

    @Resource
    private GroupService groupService;

    @Resource
    private MultiLevelCache cache;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private LockTemplate lockTemplate;

    /** 消费消息 — done 快速判断 → 安全 processing 锁 → 锁内二次判断 → 业务处理 */
    @Override
    public void onMessage(String msg) {
        Message message = null;
        try {
            message = objectMapper.readValue(msg, Message.class);
            String doneKey = "mq:consume:done:" + message.getMsgId();
            if (Boolean.TRUE.equals(redisTemplate.hasKey(doneKey))) {
                log.info("Message already consumed (idempotent skip): msgId={}", message.getMsgId());
                return;
            }
            Message processingMessage = message;
            lockTemplate.executeWithLock(
                    "mq:consume:processing:" + processingMessage.getMsgId(), 1, 60, () -> {
                        // 关闭第一次 hasKey 与成功获取锁之间的竞态窗口。
                        if (Boolean.TRUE.equals(redisTemplate.hasKey(doneKey))) {
                            return null;
                        }

                        updateConversations(processingMessage);
                        messageMapper.advanceStatus(processingMessage.getMsgId(), MessageStatus.SENT.getCode());
                        processingMessage.setStatus(MessageStatus.SENT.getCode());
                        cache.evict("msg:" + processingMessage.getMsgId());
                        observerManager.notifyObservers(processingMessage);
                        redisTemplate.opsForValue().set(doneKey, "1", 7, TimeUnit.DAYS);
                        log.info("Message persisted and pushed: msgId={}", processingMessage.getMsgId());
                        return null;
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize message from RocketMQ", e);
        } catch (Exception e) {
            log.error("Consumer processing failed, will retry: msgId={}",
                    message != null ? message.getMsgId() : "unknown", e);
            throw e; // 抛出触发 RocketMQ 重试
        }
    }

    private void updateConversations(Message message) {
        if (message.getReceiverType() == ReceiverType.GROUP.getCode()) {
            groupService.listMembers(message.getReceiverId()).forEach(member ->
                    conversationService.updateConversation(member.getUserId(), message.getReceiverId(),
                            ReceiverType.GROUP.getCode(), message.getMsgId(),
                            !member.getUserId().equals(message.getSenderId())));
        } else {
            conversationService.updateConversation(message.getSenderId(), message.getReceiverId(),
                    ReceiverType.SINGLE.getCode(), message.getMsgId(), false);
            conversationService.updateConversation(message.getReceiverId(), message.getSenderId(),
                    ReceiverType.SINGLE.getCode(), message.getMsgId(), true);
        }
    }
}
