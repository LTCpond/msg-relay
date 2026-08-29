package com.ltcpond.msgrelay.im.netty;

import com.ltcpond.msgrelay.im.config.HeartbeatConfig;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * WebSocket Channel 初始化器 — 配置 Netty Pipeline 处理器链
 *
 * Pipeline 顺序：
 * 1. HttpServerCodec: HTTP 编解码
 * 2. ChunkedWriteHandler: 分块写支持
 * 3. HttpObjectAggregator: HTTP 消息聚合
 * 4. WebSocketServerProtocolHandler: WebSocket 协议升级与帧处理
 * 5. IdleStateHandler: 空闲检测（writerIdle 触发 Ping，readerIdle 兜底断连）
 * 6. HeartbeatHandler: 控制帧心跳（Ping/Pong + 连续 miss 断连）
 * 7. WebSocketHandler: 业务处理（AUTH、消息收发）
 *
 * 心跳机制（当 heartbeat.enabled=true 时）：
 * - writerIdle(T): 到期时 HeartbeatHandler 发送 Ping 控制帧
 * - readerIdle(3T): 兜底保险丝，仅在 EventLoop 完全无入站时触发断连
 * - Pong 超时与 miss 计数由 HeartbeatHandler 内部管理
 */
@Component
public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Resource
    private WebSocketHandler webSocketHandler;

    @Resource
    private HeartbeatHandler heartbeatHandler;

    @Resource
    private HeartbeatConfig heartbeatConfig;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        // HTTP 编解码与聚合
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(new HttpObjectAggregator(65536));

        // WebSocket 协议升级（路径 /ws）
        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));

        // 心跳：根据配置决定启用控制帧心跳还是旧的简单超时
        if (heartbeatConfig.isEnabled()) {
            int T = heartbeatConfig.getIntervalSeconds();
            // 控制帧心跳：writerIdle=T 触发 Ping，readerIdle=3T 兜底
            pipeline.addLast(new IdleStateHandler(T * 3, T, 0, TimeUnit.SECONDS));
            pipeline.addLast(heartbeatHandler);
        } else {
            // 回退到旧行为：30s 读空闲直接断连
            pipeline.addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
        }

        // 业务处理（AUTH、消息收发）
        pipeline.addLast(webSocketHandler);
    }
}
