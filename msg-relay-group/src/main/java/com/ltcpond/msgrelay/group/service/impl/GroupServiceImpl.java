package com.ltcpond.msgrelay.group.service.impl;

import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import com.ltcpond.msgrelay.group.model.entity.Group;
import com.ltcpond.msgrelay.group.model.entity.GroupMember;
import com.ltcpond.msgrelay.group.repository.GroupMapper;
import com.ltcpond.msgrelay.group.repository.GroupMemberMapper;
import com.ltcpond.msgrelay.group.service.GroupMemberIndexService;
import com.ltcpond.msgrelay.group.service.GroupConversationLifecycle;
import com.ltcpond.msgrelay.group.service.GroupService;
import com.ltcpond.msgrelay.user.model.entity.User;
import com.ltcpond.msgrelay.user.repository.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class GroupServiceImpl implements GroupService {

    @Resource
    private GroupMapper groupMapper;

    @Resource
    private GroupMemberMapper groupMemberMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private SnowflakeIdGenerator idGenerator;

    @Resource
    private MultiLevelCache cache;

    @Resource
    private GroupMemberIndexService groupMemberIndexService;

    @Resource
    private GroupConversationLifecycle groupConversationLifecycle;

    private static final int LARGE_GROUP_THRESHOLD = 500;
    private static final long MEMBER_LIST_TTL = 300;

    @Override
    public Group create(Long ownerId, String name) {
        User owner = userMapper.selectById(ownerId);
        if (owner == null || owner.getTeamId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "用户未加入任何团队，无法创建群聊");
        }

        Group group = new Group();
        group.setId(idGenerator.nextId());
        group.setConversationId(idGenerator.nextId());
        group.setOwnerId(ownerId);
        group.setTeamId(owner.getTeamId());
        group.setName(name);
        group.setMaxMembers(2000);
        group.setIsMutedAll(false);
        group.setDeleted(0);
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        groupMapper.insert(group);

        GroupMember member = new GroupMember();
        member.setId(idGenerator.nextId());
        member.setGroupId(group.getId());
        member.setUserId(ownerId);
        member.setRole(1);
        member.setIsMuted(false);
        member.setDeleted(0);
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        groupMemberMapper.insert(member);

        groupConversationLifecycle.create(group.getConversationId(), group.getId(), ownerId);

        redisTemplate.opsForSet().add("group:members:" + group.getId(), ownerId.toString());
        groupMemberIndexService.assignMemberIndex(group.getId(), ownerId);
        return group;
    }

    @Override
    public Group getById(Long groupId) {
        return groupMapper.selectById(groupId);
    }

    @Override
    public void addMember(Long groupId, Long userId, Long operatorId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群组不存在");
        }
        checkOwnerOrAdmin(groupId, operatorId);

        // 校验被邀请人与群同团队
        User invitee = userMapper.selectById(userId);
        if (invitee == null || !invitee.getTeamId().equals(group.getTeamId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能邀请同一团队的成员加入群聊");
        }

        Long count = groupMemberMapper.countByGroupId(groupId);
        if (count >= group.getMaxMembers()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "群成员已满");
        }

        GroupMember exist = groupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (exist != null) {
            throw new BusinessException(ResultCode.CONFLICT, "成员已在群中");
        }

        GroupMember member = new GroupMember();
        member.setId(idGenerator.nextId());
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(3);
        member.setIsMuted(false);
        member.setDeleted(0);
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        groupMemberMapper.insert(member);
        groupConversationLifecycle.addMember(group.getConversationId(), userId);
        redisTemplate.opsForSet().add("group:members:" + groupId, userId.toString());

        // 为新成员分配 Bitmap 位序号
        groupMemberIndexService.assignMemberIndex(groupId, userId);
    }

    @Override
    public void removeMember(Long groupId, Long userId, Long operatorId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群组不存在");
        }
        checkOwnerOrAdmin(groupId, operatorId);

        if (group.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能移除群主");
        }

        groupMemberMapper.deleteByGroupIdAndUserId(groupId, userId);
        groupConversationLifecycle.removeMember(group.getConversationId(), userId);
        redisTemplate.opsForSet().remove("group:members:" + groupId, userId.toString());

        // 处理 Bitmap 退群
        groupMemberIndexService.handleLeaveGroup(groupId, userId);
    }

    @Override
    public void muteMember(Long groupId, Long userId, int minutes, Long operatorId) {
        checkOwnerOrAdmin(groupId, operatorId);

        GroupMember member = groupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "成员不在群中");
        }
        member.setIsMuted(true);
        member.setMuteExpireAt(LocalDateTime.now().plusMinutes(minutes));
        member.setUpdatedAt(LocalDateTime.now());
        groupMemberMapper.updateById(member);
    }

    @Override
    public void setRole(Long groupId, Long userId, int role, Long operatorId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群组不存在");
        }
        if (!group.getOwnerId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有群主可以设置角色");
        }
        if (operatorId.equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能修改自己的角色");
        }
        if (role != 2 && role != 3) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的角色类型");
        }

        GroupMember member = groupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "成员不在群中");
        }
        member.setRole(role);
        member.setUpdatedAt(LocalDateTime.now());
        groupMemberMapper.updateById(member);
    }

    @Override
    public void transferOwner(Long groupId, Long newOwnerId, Long operatorId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群组不存在");
        }
        if (!group.getOwnerId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有群主可以转让");
        }

        GroupMember newOwner = groupMemberMapper.selectByGroupIdAndUserId(groupId, newOwnerId);
        if (newOwner == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "目标用户不在群中");
        }

        GroupMember oldOwner = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        oldOwner.setRole(2);
        oldOwner.setUpdatedAt(LocalDateTime.now());
        groupMemberMapper.updateById(oldOwner);

        newOwner.setRole(1);
        newOwner.setUpdatedAt(LocalDateTime.now());
        groupMemberMapper.updateById(newOwner);

        group.setOwnerId(newOwnerId);
        group.setUpdatedAt(LocalDateTime.now());
        groupMapper.updateById(group);
    }

    @Override
    public void muteAll(Long groupId, boolean muted, Long operatorId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群组不存在");
        }
        checkOwnerOrAdmin(groupId, operatorId);

        group.setIsMutedAll(muted);
        group.setUpdatedAt(LocalDateTime.now());
        groupMapper.updateById(group);
    }

    @Override
    public void deleteGroup(Long groupId, Long operatorId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群组不存在");
        }
        if (!group.getOwnerId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有群主可以删除群聊");
        }

        groupMapper.deleteByIdLogic(groupId);
        groupConversationLifecycle.delete(group.getConversationId());
        redisTemplate.delete("group:members:" + groupId);
    }

    @Override
    public void leaveGroup(Long groupId, Long userId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群组不存在");
        }
        if (group.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "群主不能退出，请先转让群主");
        }
        groupMemberMapper.deleteByGroupIdAndUserId(groupId, userId);
        groupConversationLifecycle.removeMember(group.getConversationId(), userId);
        redisTemplate.opsForSet().remove("group:members:" + groupId, userId.toString());

        // 处理 Bitmap 退群
        groupMemberIndexService.handleLeaveGroup(groupId, userId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<GroupMember> listMembers(Long groupId) {
        String cacheKey = "group:members:list:" + groupId;
        return cache.get(cacheKey, List.class,
                key -> groupMemberMapper.selectByGroupId(groupId), MEMBER_LIST_TTL);
    }

    @Override
    public boolean isLargeGroup(Long groupId) {
        Long count = groupMemberMapper.countByGroupId(groupId);
        return count >= LARGE_GROUP_THRESHOLD;
    }

    @Override
    public Set<String> getMemberIds(Long groupId) {
        return redisTemplate.opsForSet().members("group:members:" + groupId)
                .stream().map(Object::toString).collect(Collectors.toSet());
    }

    private void checkOwnerOrAdmin(Long groupId, Long operatorId) {
        GroupMember opMember = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        if (opMember == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "你不是群成员");
        }
        if (opMember.getRole() != 1 && opMember.getRole() != 2) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有群主和管理员可以操作");
        }
    }
}
