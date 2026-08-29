package com.ltcpond.msgrelay.im.config;

import com.ltcpond.msgrelay.common.constants.RedisChannel;
import com.ltcpond.msgrelay.im.netty.KickChannelHandler;
import com.ltcpond.msgrelay.im.netty.PushMessageHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Pub/Sub 配置 — 用于跨节点踢人通知
 *
 * 踢人流程:
 * 1. 用户服务发布踢人消息到 "msg-relay:kick:user" 频道
 * 2. 所有 Netty 节点订阅该频道
 * 3. 收到消息后关闭对应设备的 WebSocket 连接
 */
@Configuration
public class RedisPubSubConfig {

    @Bean
    public ChannelTopic kickTopic() {
        return new ChannelTopic(RedisChannel.KICK_CHANNEL);
    }

    @Bean
    public ChannelTopic pushTopic() {
        return new ChannelTopic(RedisChannel.PUSH_CHANNEL);
    }

    @Bean
    public MessageListenerAdapter kickListenerAdapter(KickChannelHandler handler) {
        return new MessageListenerAdapter(handler, "onMessage");
    }

    @Bean
    public MessageListenerAdapter pushListenerAdapter(PushMessageHandler handler) {
        return new MessageListenerAdapter(handler, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter kickListenerAdapter,
            ChannelTopic kickTopic,
            MessageListenerAdapter pushListenerAdapter,
            ChannelTopic pushTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(kickListenerAdapter, kickTopic);
        container.addMessageListener(pushListenerAdapter, pushTopic);
        return container;
    }

    @Bean
    public RedisTemplate<String, Object> kickRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
