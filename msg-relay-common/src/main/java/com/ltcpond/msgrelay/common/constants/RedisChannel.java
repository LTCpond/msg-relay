package com.ltcpond.msgrelay.common.constants;

/**
 * Redis Pub/Sub 频道常量
 */
public final class RedisChannel {

    /** 踢人消息频道 */
    public static final String KICK_CHANNEL = "msg-relay:kick:user";

    /** 节点定向推送频道前缀，避免所有 Netty 节点消费每一条消息。 */
    public static final String PUSH_CHANNEL_PREFIX = "msg-relay:push:node:";

    public static String pushChannel(String nodeId) {
        return PUSH_CHANNEL_PREFIX + nodeId;
    }

    private RedisChannel() {}
}
