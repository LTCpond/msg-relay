package com.ltcpond.msgrelay.im.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Netty WebSocket 服务器 — 独立于 Tomcat 运行在 8090 端口
 *
 * 线程模型:
 * - bossGroup (1 线程): 接收新连接
 * - workerGroup (默认 CPU 核数*2): 处理读写、心跳、业务逻辑
 *
 */
@Slf4j
@Component
public class WebSocketServer {

    @Value("${msg-relay.netty.ws-port:8090}")
    private int port;

    @Resource
    private WebSocketChannelInitializer channelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture future;

    /** 启动 Netty 服务器 — 绑定端口，bossGroup=1 线程，workerGroup=CPU*2 线程 */
    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)       // 连接队列长度
                .childOption(ChannelOption.SO_KEEPALIVE, true) // TCP 保活
                .childOption(ChannelOption.TCP_NODELAY, true)  // 关闭 Nagle 算法，减少延迟
                .childHandler(channelInitializer);

        future = bootstrap.bind(port).sync();
        log.info("WebSocket server started on port {}", port);
    }

    /** 优雅关闭 — 关闭 Channel 和 EventLoopGroup */
    @PreDestroy
    public void stop() {
        if (future != null) {
            future.channel().close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("WebSocket server stopped");
    }
}
