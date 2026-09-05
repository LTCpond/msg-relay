package com.ltcpond.msgrelay.common.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 每个实例订阅统一的 L1 缓存失效频道。 */
@Configuration
public class CacheInvalidationPubSubConfig {

    @Bean(name = "cacheInvalidationListenerContainer")
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory,
            CacheInvalidationSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(MultiLevelCache.INVALIDATION_CHANNEL));
        return container;
    }
}
