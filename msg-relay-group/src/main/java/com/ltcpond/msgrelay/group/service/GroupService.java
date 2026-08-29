package com.ltcpond.msgrelay.group.service;

import com.ltcpond.msgrelay.group.model.entity.Group;
import com.ltcpond.msgrelay.group.model.entity.GroupMember;

import java.util.List;
import java.util.Set;

public interface GroupService {

    /** 创建群 + 群主自动入群 */
    Group create(Long ownerId, String name);

    /** 查询群信息 */
    Group getById(Long groupId);

    /** 添加成员 — 需要群主或管理员权限 */
    void addMember(Long groupId, Long userId, Long operatorId);

    /** 移除成员 — 需要群主或管理员权限，不能移除群主 */
    void removeMember(Long groupId, Long userId, Long operatorId);

    /** 禁言成员 — 需要群主或管理员权限 */
    void muteMember(Long groupId, Long userId, int minutes, Long operatorId);

    /** 设置成员角色 — 只有群主可以操作，role: 2=管理员 3=普通成员 */
    void setRole(Long groupId, Long userId, int role, Long operatorId);

    /** 转让群主 — 只有群主可以操作，原群主降为管理员 */
    void transferOwner(Long groupId, Long newOwnerId, Long operatorId);

    /** 全员禁言开关 — 需要群主或管理员权限 */
    void muteAll(Long groupId, boolean muted, Long operatorId);

    /** 删除群聊 — 只有群主可以操作 */
    void deleteGroup(Long groupId, Long operatorId);

    /** 退出群聊 — 群主不能退出，需先转让 */
    void leaveGroup(Long groupId, Long userId);

    /** 查询成员列表 — 三级缓存加速 */
    List<GroupMember> listMembers(Long groupId);

    /** 判断是否大群（≥500人切换拉模式） */
    boolean isLargeGroup(Long groupId);

    /** 获取群成员ID集合（Redis Set） */
    Set<String> getMemberIds(Long groupId);
}
