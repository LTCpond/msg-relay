package com.ltcpond.msgrelay.group.service.impl;

import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import com.ltcpond.msgrelay.group.model.entity.GroupMemberIndex;
import com.ltcpond.msgrelay.group.repository.GroupMemberIndexMapper;
import com.ltcpond.msgrelay.group.service.GroupMemberIndexService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class GroupMemberIndexServiceImpl implements GroupMemberIndexService {

    @Resource
    private GroupMemberIndexMapper memberIndexMapper;

    @Resource
    private SnowflakeIdGenerator idGenerator;

    @Override
    public int assignMemberIndex(Long groupId, Long userId) {
        // 检查是否已有位序号（退群后重新入群）
        GroupMemberIndex existing = memberIndexMapper.selectByGroupAndUser(groupId, userId);

        if (existing != null) {
            if (existing.getStatus() == 2) {
                // 重新激活
                memberIndexMapper.updateStatus(groupId, userId, 1);
                memberIndexMapper.updateJoinedAt(groupId, userId);
                log.info("Reactivated member index: groupId={}, userId={}, index={}", groupId, userId, existing.getMemberIndex());
                return existing.getMemberIndex();
            }
            return existing.getMemberIndex();
        }

        // 分配新位序号（只增不复用）
        Integer maxIndex = memberIndexMapper.getMaxIndex(groupId);
        int newIndex = (maxIndex == null) ? 0 : maxIndex + 1;

        GroupMemberIndex record = new GroupMemberIndex();
        record.setId(idGenerator.nextId());
        record.setGroupId(groupId);
        record.setUserId(userId);
        record.setMemberIndex(newIndex);
        record.setStatus(1);
        record.setJoinedAt(LocalDateTime.now());
        memberIndexMapper.insert(record);

        log.info("Assigned member index: groupId={}, userId={}, index={}", groupId, userId, newIndex);
        return newIndex;
    }

    @Override
    public void handleLeaveGroup(Long groupId, Long userId) {
        // 标记为已退出，保留位序号映射
        memberIndexMapper.updateStatus(groupId, userId, 2);
        memberIndexMapper.updateLeftAt(groupId, userId);
        log.info("Handled leave group: groupId={}, userId={}", groupId, userId);
    }

    @Override
    public void handleRejoinGroup(Long groupId, Long userId) {
        GroupMemberIndex existing = memberIndexMapper.selectByGroupAndUser(groupId, userId);

        if (existing != null && existing.getStatus() == 2) {
            // 重新激活，保留原位序号
            memberIndexMapper.updateStatus(groupId, userId, 1);
            memberIndexMapper.updateJoinedAt(groupId, userId);
            log.info("Rejoined group with existing index: groupId={}, userId={}, index={}",
                     groupId, userId, existing.getMemberIndex());
        } else {
            // 新用户，分配新位序号
            assignMemberIndex(groupId, userId);
        }
    }
}
