package com.ltcpond.msgrelay.im.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.observer.MessageObserverManager;
import com.ltcpond.msgrelay.im.repository.MessageMapper;
import com.ltcpond.msgrelay.im.service.ConversationService;
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

            // 0. 原子幂等：SET NX，已处理过直接跳过
            idempotentKey = "mq:consume:" + message.getMsgId();
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", 604800, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(acquired)) {
                log.info("Message already consumed (idempotent skip): msgId={}", message.getMsgId());
                return;
            }

            // 1. 更新发送者和接收者的会话（双向，unreadCount 由原子 SQL 保证幂等）
            conversationService.updateConversation(
                    message.getSenderId(), message.getReceiverId(),
                    message.getReceiverType(), message.getMsgId());
            conversationService.updateConversation(
                    message.getReceiverId(), message.getSenderId(),
                    message.getReceiverType(), message.getMsgId());

            // 2. 更新消息状态为 SENT
            message.setStatus(1);
            message.setUpdatedAt(java.time.LocalDateTime.now());
            messageMapper.updateById(message);

            // 3. 推送给在线用户
            observerManager.notifyObservers(message);
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
}
