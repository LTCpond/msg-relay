package com.ltcpond.msgrelay.im.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 在线状态（presence）配置 — 读取 msg-relay.presence.* 配置项
 *
 * mode=zset 时使用 ZSET + Lua 方案，无 KEYS 阻塞风险
 * mode=legacy 时使用旧的 online:{userId}:{channelId} 方案
 */
@Data
@Component
@ConfigurationProperties(prefix = "msg-relay.presence")
public class PresenceConfig {

    /** 在线状态模式：zset（推荐）或 legacy（旧方案） */
    private String mode = "zset";

    /** 租约 TTL（秒）：偏准模式建议 35-45s */
    private long leaseTtlSeconds = 40;
}
