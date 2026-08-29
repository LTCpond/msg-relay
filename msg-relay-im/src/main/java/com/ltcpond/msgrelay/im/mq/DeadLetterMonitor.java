package com.ltcpond.msgrelay.im.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 死信队列监控 — 定时扫描 DLQ 并打印 ERROR 日志
 *
 * 当 MessageConsumer 消费失败 16 次后，RocketMQ 将消息投递到
 * %DLQ%msg-relay-message-consumer 死信主题。本组件定时扫描死信队列，
 * 发现死信后打印完整消息体，供运维人工介入或对接告警。
 */
@Slf4j
@Component
public class DeadLetterMonitor {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    private static final String DLQ_TOPIC = "%DLQ%msg-relay-message-consumer";

    /** 每 5 分钟扫描一次死信队列 */
    @Scheduled(fixedDelay = 300_000)
    public void scanDlq() {
        DefaultLitePullConsumer consumer = null;
        try {
            consumer = new DefaultLitePullConsumer("dlq-monitor-consumer");
            consumer.setNamesrvAddr(nameServer);
            consumer.setPullBatchSize(100);
            consumer.start();
            consumer.subscribe(DLQ_TOPIC, "*");

            List<MessageExt> messages = consumer.poll(5000);
            if (messages.isEmpty()) {
                return;
            }

            log.error("===== 发现 {} 条死信消息 =====", messages.size());
            for (MessageExt msg : messages) {
                log.error("死信消息: msgId={}, bornTimestamp={}, reconsumeTimes={}, body={}",
                        msg.getMsgId(), msg.getBornTimestamp(), msg.getReconsumeTimes(),
                        new String(msg.getBody()));
            }
            // ACK 消费（避免下次重复拉取）
            consumer.commitSync();
        } catch (Exception e) {
            log.error("DeadLetterMonitor 扫描异常", e);
        } finally {
            if (consumer != null) {
                try { consumer.shutdown(); } catch (Exception ignored) {}
            }
        }
    }
}
