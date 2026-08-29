package com.ltcpond.msgrelay.im.netty;

import com.ltcpond.msgrelay.common.utils.JwtUtils;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理 — Channel 与 User 的双向映射（支持多设备）
 *
 * channelUserMap: channelId → userId（根据 channel 找用户）
 * userChannelMap: userId → Set<channelId>（根据用户找所有 channel，用于消息推送）
 * deviceChannelMap: deviceId → channelId（根据设备找 channel，用于踢人）
 *
 * 一个用户可以同时有多个设备连接（手机、电脑、平板）
 */
@Slf4j
@Component
public class SessionManager {

    @Resource
    private JwtUtils jwtUtils;

    private final ConcurrentHashMap<String, Long> channelUserMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> userChannelMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Channel> channelMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> deviceChannelMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> channelDeviceMap = new ConcurrentHashMap<>();

    /** 认证: 解析 JWT → 建立双向映射 */
    public Long authenticate(Channel channel, String token, String deviceId) {
        try {
            Long userId = jwtUtils.getUserId(token);
            String channelId = channel.id().asLongText();
            channelUserMap.put(channelId, userId);
            userChannelMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(channelId);
            channelMap.put(channelId, channel);
            channel.attr(SessionAttributes.USER_ID).set(userId);
            channel.attr(SessionAttributes.DEVICE_ID).set(deviceId);
            deviceChannelMap.put(deviceId, channelId);
            channelDeviceMap.put(channelId, deviceId);
            return userId;
        } catch (Exception e) {
            log.warn("Invalid token for WebSocket auth: {}", e.getMessage());
            return null;
        }
    }

    /** 从 Channel 属性中获取 userId */
    public Long getUserId(Channel channel) {
        return channel.attr(SessionAttributes.USER_ID).get();
    }

    /** 根据 userId 获取所有在线设备的 channelId */
    public Set<String> getChannelIds(Long userId) {
        Set<String> channelIds = userChannelMap.get(userId);
        return channelIds != null ? channelIds : Collections.emptySet();
    }

    /** 移除: 断开连接时清理双向映射 */
    public Long remove(Channel channel) {
        String channelId = channel.id().asLongText();
        Long userId = channelUserMap.remove(channelId);
        if (userId != null) {
            Set<String> channelIds = userChannelMap.get(userId);
            if (channelIds != null) {
                channelIds.remove(channelId);
                if (channelIds.isEmpty()) {
                    userChannelMap.remove(userId);
                }
            }
        }
        channelMap.remove(channelId);
        // 清理设备映射
        String deviceId = channelDeviceMap.remove(channelId);
        if (deviceId != null) {
            deviceChannelMap.remove(deviceId);
        }
        channel.attr(SessionAttributes.USER_ID).set(null);
        channel.attr(SessionAttributes.DEVICE_ID).set(null);
        return userId;
    }

    /** 根据 channelId 获取 Channel，用于消息推送 */
    public Channel getChannel(String channelId) {
        return channelMap.get(channelId);
    }

    /** 根据 deviceId 获取 Channel，用于踢人 */
    public Channel getChannelByDeviceId(String deviceId) {
        String channelId = deviceChannelMap.get(deviceId);
        return channelId != null ? getChannel(channelId) : null;
    }

    /** 根据 channelId 获取 deviceId */
    public String getDeviceId(String channelId) {
        return channelDeviceMap.get(channelId);
    }

    /** 向指定用户的所有在线设备推送文本消息 */
    public void pushToUser(Long userId, String text) {
        Set<String> channelIds = getChannelIds(userId);
        for (String channelId : channelIds) {
            Channel channel = getChannel(channelId);
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(text));
            }
        }
    }
}
