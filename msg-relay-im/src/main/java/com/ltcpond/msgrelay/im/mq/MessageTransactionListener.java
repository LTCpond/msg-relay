package com.ltcpond.msgrelay.im.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.repository.MessageMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 事务消息监听器 — 保证消息发送与落库的原子性
 *
 * 流程:
 * 1. executeLocalTransaction: 先落库 MySQL，成功则 COMMIT 投递 MQ
 * 2. checkLocalTransaction: MQ 回查时根据 msgId 反查 DB 确认状态
 */
@Slf4j
@Component
@RocketMQTransactionListener
public class MessageTransactionListener implements RocketMQLocalTransactionListener {

    @Resource
    private MessageMapper messageMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 执行本地事务 — 落库 MySQL，成功返回 COMMIT */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(org.springframework.messaging.Message msg, Object arg) {
        try {
            if (arg instanceof Message) {
                Message message = (Message) arg;
                messageMapper.insert(message);
                log.info("Local transaction executed successfully, msgId={}", message.getMsgId());
                return RocketMQLocalTransactionState.COMMIT;
            }
            return RocketMQLocalTransactionState.UNKNOWN;
        } catch (Exception e) {
            log.error("Local transaction execution failed", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /** 事务回查 — 根据 msgId 反查 DB，有记录则 COMMIT */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(org.springframework.messaging.Message msg) {
        try {
            String json = new String((byte[]) msg.getPayload());
            Message messageObj = objectMapper.readValue(json, Message.class);

            Message existing = messageMapper.selectByMsgId(messageObj.getMsgId());
            if (existing != null) {
                log.info("Local transaction check passed, msgId={}", messageObj.getMsgId());
                return RocketMQLocalTransactionState.COMMIT;
            } else {
                log.info("Local transaction check found no record, msgId={}", messageObj.getMsgId());
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        } catch (Exception e) {
            log.error("Local transaction check failed", e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
