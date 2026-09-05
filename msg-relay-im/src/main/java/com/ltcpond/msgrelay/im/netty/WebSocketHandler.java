package com.ltcpond.msgrelay.im.netty;

import com.ltcpond.msgrelay.im.service.OnlineStatusService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 * WebSocket 业务处理器 — AUTH 认证与消息收发
 *
 * 生命周期（与 HeartbeatHandler 协作）：
 * 1. 握手完成 → HeartbeatHandler 标记未认证，等待 AUTH
 * 2. 客户端发 "AUTH {jwtToken}" → 本 handler 处理 session 注册
 * 3. 认证成功 → HeartbeatHandler 发首跳 Ping，进入心跳状态机
 * 4. 后续心跳由 HeartbeatHandler 通过控制帧（Ping/Pong）管理
 * 5. 连接断开 → 本 handler 清理 session + 标记离线
 *
 * 设计变更：
 * - 心跳逻辑（Ping/Pong、miss 计数、断连判定）已移至 HeartbeatHandler
 * - 本 handler 不再处理 "PING"/"PONG" 文本协议
 * - 心跳续租 presence 由 HeartbeatHandler 在收到有效 Pong 时触发
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final SessionManager sessionManager;
    private final OnlineStatusService onlineStatusService;

    /** 构造注入（替代 @Resource，避免循环依赖） */
    public WebSocketHandler(SessionManager sessionManager,
                            OnlineStatusService onlineStatusService) {
        this.sessionManager = sessionManager;
        this.onlineStatusService = onlineStatusService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();
        String channelId = ctx.channel().id().asLongText();

        if (text.startsWith("AUTH ")) {
            // 协议只接受 AUTH {accessToken}；设备身份完全来自签名后的 JWT。
            String token = text.substring(5).trim();
            Long userId = sessionManager.authenticate(ctx.channel(), token);

            if (userId != null) {
                String deviceId = ctx.channel().attr(SessionAttributes.DEVICE_ID).get();
                ctx.channel().writeAndFlush(new TextWebSocketFrame("AUTH_OK"));
                // 标记 presence 在线（用 deviceId 作为设备标识，不再用 channelId）
                onlineStatusService.online(userId, deviceId, channelId);
                // 通知 HeartbeatHandler 认证成功，启动心跳状态机
                ctx.fireUserEventTriggered(new AuthenticationSuccessEvent());
                log.info("用户认证成功: userId={}, deviceId={}, channel={}", userId, deviceId, channelId);
            } else {
                ctx.channel().writeAndFlush(new TextWebSocketFrame("AUTH_FAIL"));
                log.warn("认证失败: channel={}", channelId);
                ctx.close();
            }
            return;
        }

        // 其他业务消息：当前架构下消息走 HTTP + MQ，WebSocket 主要做 AUTH/心跳/推送
        // 如果未来有 WebSocket 业务消息，在此处扩展
        log.debug("收到业务消息: channel={}, text={}", channelId, text.length() > 100 ? text.substring(0, 100) : text);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 连接断开 → 清理 session + 标记离线
        String channelId = ctx.channel().id().asLongText();
        String deviceId = ctx.channel().attr(SessionAttributes.DEVICE_ID).get();
        Long userId = sessionManager.remove(ctx.channel());

        if (userId != null && deviceId != null
                && sessionManager.getChannelByDeviceId(userId, deviceId) == null) {
            onlineStatusService.offline(userId, deviceId);
            log.info("设备断开: userId={}, deviceId={}, channel={}", userId, deviceId, channelId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket 异常: channel={}", ctx.channel().id().asShortText(), cause);
        ctx.close();
    }
}
