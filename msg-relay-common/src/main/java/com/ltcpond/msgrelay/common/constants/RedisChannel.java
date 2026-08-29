package com.ltcpond.msgrelay.common.constants;

/**
 * Redis Pub/Sub 频道常量
 */
public final class RedisChannel {

    /** 踢人消息频道 */
    public static final String KICK_CHANNEL = "msg-relay:kick:user";

    /** 跨节点消息推送频道 */
    public static final String PUSH_CHANNEL = "msg-relay:push:msg";

    private RedisChannel() {}
}
