package com.ltcpond.msgrelay.im.netty;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 跨节点消息推送处理器 — 订阅 Redis Pub/Sub 频道，接收推送指令并本地投递
 *
 * 消息格式: "userId|jsonPayload"
 * 收到消息后调用 SessionManager.pushToUser() 尝试本地投递：
 * - 如果目标用户在本节点有连接 → 投递成功
 * - 如果目标用户不在本节点 → pushToUser 遍历空集合，空操作
 */
@Slf4j
@Component
public class PushMessageHandler implements MessageListener {

    @Resource
    private SessionManager sessionManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            int sepIdx = body.indexOf('|');
            if (sepIdx <= 0) {
                log.warn("Invalid push message format: {}", body);
                return;
            }
            Long userId = Long.valueOf(body.substring(0, sepIdx));
            String jsonPayload = body.substring(sepIdx + 1);
            sessionManager.pushToUser(userId, jsonPayload);
        } catch (Exception e) {
            log.error("Failed to process push message", e);
        }
    }
}
