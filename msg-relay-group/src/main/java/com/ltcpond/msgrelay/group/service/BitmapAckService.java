package com.ltcpond.msgrelay.group.service;

import java.util.List;

/**
 * Bitmap ACK 服务 — 群聊消息投递/已读状态管理
 *
 * 三阶段 ACK:
 * - SENT: 消息投递事件已由 Consumer 接受（status=1）
 * - DELIVERED: 消息推送到客户端，更新 delivered_bitmap
 * - READ: 用户已读，更新 read_bitmap
 */
public interface BitmapAckService {

    /**
     * 标记消息已投递（Bitmap 方式）
     */
    void markDelivered(Long msgId, Long userId, Long groupId);

    /**
     * 标记消息已读（Bitmap 方式）
     */
    void markRead(Long msgId, Long userId, Long groupId);

    /**
     * 查询已投递用户列表
     */
    List<Long> getDeliveredUsers(Long msgId, Long groupId);

    /**
     * 查询已读用户列表
     */
    List<Long> getReadUsers(Long msgId, Long groupId);

    /**
     * 查询未读用户列表
     */
    List<Long> getUnreadUsers(Long msgId, Long groupId);

    /**
     * 获取已投递人数
     */
    int getDeliveredCount(Long msgId);

    /**
     * 获取已读人数
     */
    int getReadCount(Long msgId);
}
