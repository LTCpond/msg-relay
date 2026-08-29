package com.ltcpond.msgrelay.im.service;

import java.util.List;
import java.util.Map;

/**
 * 在线状态服务接口（支持多设备）
 *
 * 设备标识从 channelId 改为 deviceId（AUTH 时客户端传入）
 * 原因：channelId 每次重连都会变，deviceId 才是稳定的设备标识
 */
public interface OnlineStatusService {

    /** 设备上线 — 注册到 ZSET，score=now+leaseTTL */
    void online(Long userId, String deviceId, String channelId);

    /** 设备离线 — 从 ZSET 移除 */
    void offline(Long userId, String deviceId);

    /** 心跳续期 — 更新 ZSET 中的 score 和设备元信息 */
    void heartbeat(Long userId, String deviceId);

    /** 判断用户是否有任意设备在线 */
    boolean isOnline(Long userId);

    /** 批量查询用户在线状态 */
    Map<Long, Boolean> batchIsOnline(List<Long> userIds);

    /** 获取设备所在节点 ID */
    String getNodeId(Long userId, String deviceId);
}
