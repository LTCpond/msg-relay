package com.ltcpond.msgrelay.common.cache;

import jakarta.annotation.Resource;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** 接收跨实例缓存失效广播，只清理当前 JVM 的 Caffeine L1。 */
@Component
public class CacheInvalidationSubscriber implements MessageListener {

    @Resource
    private MultiLevelCache cache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        cache.invalidateLocal(new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
