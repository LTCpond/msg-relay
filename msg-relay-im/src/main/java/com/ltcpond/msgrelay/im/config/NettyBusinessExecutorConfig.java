package com.ltcpond.msgrelay.im.config;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 阻塞式鉴权、MySQL 与 Redis 操作使用独立业务线程池，避免占用 Netty EventLoop。 */
@Configuration
public class NettyBusinessExecutorConfig {

    @Bean(name = "nettyBusinessExecutor", destroyMethod = "shutdownGracefully")
    public EventExecutorGroup nettyBusinessExecutor(
            @Value("${msg-relay.netty.business-threads:16}") int threads) {
        return new DefaultEventExecutorGroup(Math.max(2, threads));
    }
}
