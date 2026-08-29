package com.ltcpond.msgrelay.im.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.im.model.entity.Message;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 消息生产者 — 将消息投递到 RocketMQ
 *
 * 作用: 将消息发送操作从 WebSocket 线程中解耦
 * - 发送线程: 先落库 → 投递 MQ → 立即返回 ACK 给用户
 * - 消费线程: 异步消费 → 更新会话 → 推送给目标用户
 */
@Slf4j
@Component
public class MessageProducer {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MSG_TOPIC = "msg-relay-message-topic";

    /** 发送事务消息 — 序列化消息体后投递到 RocketMQ */
    public TransactionSendResult sendMessage(Message message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            org.springframework.messaging.Message<String> springMessage = org.springframework.messaging.support.MessageBuilder.withPayload(json).build();
            TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(MSG_TOPIC, springMessage, message);
            if (result == null
                    || result.getSendStatus() != SendStatus.SEND_OK
                    || result.getLocalTransactionState() != LocalTransactionState.COMMIT_MESSAGE) {
                log.error("Transactional message send failed: msgId={}, sendStatus={}, txState={}",
                        message.getMsgId(),
                        result != null ? result.getSendStatus() : null,
                        result != null ? result.getLocalTransactionState() : null);
                throw new BusinessException(ResultCode.MSG_SEND_FAILED);
            }

            log.info("Transactional message committed: msgId={}, result={}", message.getMsgId(), result.getSendStatus());
            return result;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message: msgId={}", message.getMsgId(), e);
            throw new BusinessException(ResultCode.MSG_SEND_FAILED, "消息序列化失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send transactional message: msgId={}", message.getMsgId(), e);
            throw new BusinessException(ResultCode.MSG_SEND_FAILED);
        }
    }
}
