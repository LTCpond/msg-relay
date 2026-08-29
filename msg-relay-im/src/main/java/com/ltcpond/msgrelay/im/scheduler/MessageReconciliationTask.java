package com.ltcpond.msgrelay.im.scheduler;

import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.enums.MessageStatus;
import com.ltcpond.msgrelay.im.observer.MessageObserverManager;
import com.ltcpond.msgrelay.im.repository.MessageMapper;
import com.ltcpond.msgrelay.im.service.ConversationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息补偿任务 — 定时扫描长时间未投递成功的消息并直接处理
 *
 * 触发条件: t_message 中 status=0 且 created_at 超过 1 小时的消息。
 * 处理方式: 直接执行 Consumer 的业务逻辑（更新会话 + 推送），不复用
 * MessageProducer（避免重复 INSERT 导致的 duplicate key）。
 */
@Slf4j
@Component
public class MessageReconciliationTask {

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private ConversationService conversationService;

    @Resource
    private MessageObserverManager observerManager;

    /** 每小时扫描一次 */
    @Scheduled(fixedDelay = 3_600_000)
    public void reconcileStaleMessages() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
            List<Message> staleMessages = messageMapper.selectStaleMessages(
                    MessageStatus.SENDING.getCode(), cutoff.toString());

            if (staleMessages.isEmpty()) {
                return;
            }

            log.warn("发现 {} 条超时未投递消息，直接执行补偿", staleMessages.size());
            for (Message msg : staleMessages) {
                try {
                    // 直接执行 Consumer 业务逻辑（不重新 INSERT）
                    conversationService.updateConversation(
                            msg.getSenderId(), msg.getReceiverId(),
                            msg.getReceiverType(), msg.getMsgId());
                    conversationService.updateConversation(
                            msg.getReceiverId(), msg.getSenderId(),
                            msg.getReceiverType(), msg.getMsgId());
                    msg.setStatus(MessageStatus.SENT.getCode());
                    msg.setUpdatedAt(LocalDateTime.now());
                    messageMapper.updateById(msg);
                    observerManager.notifyObservers(msg);
                    log.info("已补偿处理超时消息: msgId={}", msg.getMsgId());
                } catch (Exception e) {
                    log.error("补偿处理失败: msgId={}", msg.getMsgId(), e);
                }
            }
        } catch (Exception e) {
            log.error("MessageReconciliationTask 执行异常", e);
        }
    }
}
