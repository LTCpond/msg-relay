package com.ltcpond.msgrelay.group.service;

/**
 * 群组模块对会话模块的生命周期端口。
 * 接口由 im 模块实现，避免模块循环依赖。
 */
public interface GroupConversationLifecycle {

    void create(Long conversationId, Long groupId, Long ownerId);

    void addMember(Long conversationId, Long userId);

    void removeMember(Long conversationId, Long userId);

    void delete(Long conversationId);
}
