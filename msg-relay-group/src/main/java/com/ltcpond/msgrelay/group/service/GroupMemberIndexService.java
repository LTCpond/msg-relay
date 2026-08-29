package com.ltcpond.msgrelay.group.service;

/**
 * 群成员位序号服务 — 管理 Bitmap 位序号分配
 */
public interface GroupMemberIndexService {

    /**
     * 分配群成员位序号（只增不复用）
     * @return 位序号
     */
    int assignMemberIndex(Long groupId, Long userId);

    /**
     * 处理成员退群（标记为已退出，保留位序号）
     */
    void handleLeaveGroup(Long groupId, Long userId);

    /**
     * 处理成员重新入群
     */
    void handleRejoinGroup(Long groupId, Long userId);
}
