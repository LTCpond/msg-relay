package com.ltcpond.msgrelay.im.netty;

import com.ltcpond.msgrelay.im.service.OnlineStatusService;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 踢人消息处理器 — 订阅 Redis Pub/Sub 频道，接收踢人指令
 *
 * 消息格式: "userId:deviceId"
 * 收到消息后:
 * 1. 根据 deviceId 找到对应的 WebSocket Channel
 * 2. 发送 "KICKED" 指令通知客户端
 * 3. 清理 Redis ZSET 在线状态
 * 4. 关闭连接
 *
 * 客户端收到 "KICKED" 后应展示"您的账号在其他设备登录"提示
 */
@Slf4j
@Component
public class KickChannelHandler implements MessageListener {

    @Resource
    private SessionManager sessionManager;

    @Resource
    private OnlineStatusService onlineStatusService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            String[] parts = body.split(":");
            if (parts.length != 2) {
                log.warn("Invalid kick message format: {}", body);
                return;
            }

            Long userId = Long.valueOf(parts[0]);
            String deviceId = parts[1];

            // 主动清理 Redis ZSET，消除 40s 残留窗口
            onlineStatusService.offline(userId, deviceId);

            // 根据 deviceId 找到对应的 Channel
            Channel channel = sessionManager.getChannelByDeviceId(deviceId);
            if (channel != null && channel.isActive()) {
                // 发送 KICKED 指令
                channel.writeAndFlush(new TextWebSocketFrame("KICKED"));
                // 关闭连接
                channel.close();
                log.info("Kicked device: userId={}, deviceId={}", userId, deviceId);
            } else {
                log.info("Device not found or already disconnected: userId={}, deviceId={}", userId, deviceId);
            }
        } catch (Exception e) {
            log.error("Failed to process kick message", e);
        }
    }
}
