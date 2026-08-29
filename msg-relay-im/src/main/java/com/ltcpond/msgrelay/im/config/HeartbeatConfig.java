package com.ltcpond.msgrelay.im.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 心跳配置 — 读取 msg-relay.netty.heartbeat.* 配置项
 *
 * 控制帧心跳机制参数：
 * - enabled: 是否启用（false 则回退到旧行为）
 * - intervalSeconds: 服务端 Ping 控制帧周期（T）
 * - pongTimeoutSeconds: 等待 Pong 回复的超时（P）
 * - maxMisses: 连续未收到 Pong 的次数阈值（N）
 */
@Data
@Component
@ConfigurationProperties(prefix = "msg-relay.netty.heartbeat")
public class HeartbeatConfig {

    /** 是否启用控制帧心跳，默认 true */
    private boolean enabled = true;

    /** 服务端发 Ping 控制帧的周期（秒），默认 20s */
    private int intervalSeconds = 20;

    /** 等待 Pong 回复的超时（秒），默认 6s */
    private int pongTimeoutSeconds = 6;

    /** 连续未收到 Pong 的次数阈值，达到后断连，默认 2 */
    private int maxMisses = 2;
}
