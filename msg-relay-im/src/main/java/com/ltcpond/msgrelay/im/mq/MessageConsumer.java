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

    /** 消费消息 — SETNX 幂等 → 业务处理 → 失败删 key 允许重试 */
    @Override
    public void onMessage(String msg) {
        Message message = null;
        String idempotentKey = null;
        try {
            message = objectMapper.readValue(msg, Message.class);

            String doneKey = "mq:consume:done:" + message.getMsgId();
            if (Boolean.TRUE.equals(redisTemplate.hasKey(doneKey))) {
                log.info("Message already consumed (idempotent skip): msgId={}", message.getMsgId());
                return;
            }
            // processing 锁只覆盖本次处理；done 标记只在全部业务完成后写入。
            idempotentKey = "mq:consume:processing:" + message.getMsgId();
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", 60, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(acquired)) {
                throw new IllegalStateException("消息正在由其他消费者处理");
            }

            updateConversations(message);

            // 2. 更新消息状态为 SENT
            messageMapper.advanceStatus(message.getMsgId(), MessageStatus.SENT.getCode());
            message.setStatus(MessageStatus.SENT.getCode());
            cache.evict("msg:" + message.getMsgId());

            // 3. 推送给在线用户
            observerManager.notifyObservers(message);
            redisTemplate.opsForValue().set(doneKey, "1", 7, TimeUnit.DAYS);
            redisTemplate.delete(idempotentKey);
            log.info("Message persisted and pushed: msgId={}", message.getMsgId());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize message from RocketMQ", e);
        } catch (Exception e) {
            // 业务失败：删除幂等 key，让 RocketMQ 重试
            log.error("Consumer processing failed, will retry: msgId={}",
                    message != null ? message.getMsgId() : "unknown", e);
            if (idempotentKey != null) {
                redisTemplate.delete(idempotentKey);
            }
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
