package com.ltcpond.msgrelay.user.service.impl;
import com.ltcpond.msgrelay.user.service.DepartmentService;

import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import com.ltcpond.msgrelay.user.model.dto.CreateDeptRequest;
import com.ltcpond.msgrelay.user.model.entity.Department;
import com.ltcpond.msgrelay.user.model.entity.User;
import com.ltcpond.msgrelay.user.model.vo.DepartmentVO;
import com.ltcpond.msgrelay.user.model.vo.UserVO;
import com.ltcpond.msgrelay.user.repository.DepartmentMapper;
import com.ltcpond.msgrelay.user.repository.TeamMapper;
import com.ltcpond.msgrelay.user.repository.UserMapper;
import com.ltcpond.msgrelay.user.model.entity.Team;
import com.ltcpond.msgrelay.user.service.TeamService;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    @Resource
    private DepartmentMapper departmentMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private TeamService teamService;

    @Resource
    private MultiLevelCache multiLevelCache;

    @Resource
    private SnowflakeIdGenerator idGenerator;

    @Override
    public DepartmentVO create(CreateDeptRequest request) {
        // 仅 owner/admin 可创建部门（权限在校验层处理）
        Department dept = new Department();
        dept.setId(idGenerator.nextId());
        dept.setTeamId(request.getTeamId());
        dept.setName(request.getName());
        dept.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        dept.setDeleted(0);
        dept.setCreatedAt(LocalDateTime.now());
        dept.setUpdatedAt(LocalDateTime.now());
        departmentMapper.insert(dept);
        return toVO(dept);
    }

    @Override
    public List<DepartmentVO> listByTeamId(Long teamId) {
        String cacheKey = "dept:list:" + teamId;
        return multiLevelCache.get(cacheKey, List.class,
                key -> departmentMapper.selectByTeamId(teamId).stream()
                        .map(this::toVO)
                        .collect(Collectors.toList()),
                600);
    }

    @Override
    public DepartmentVO getByUserId(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeptId() == null) {
            return null;
        }
        Department dept = departmentMapper.selectById(user.getDeptId());
        return dept != null ? toVO(dept) : null;
    }

    @Override
    public void delete(Long deptId, Long userId) {
        Department dept = departmentMapper.selectById(deptId);
        if (dept == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }
        Team team = teamMapper.selectById(dept.getTeamId());
        if (team == null || !team.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有团队所有者才能删除部门");
        }
        departmentMapper.deleteByIdLogic(deptId);
    }

    @Override
    public void changeDept(Long memberId, Long newDeptId, Long operatorId) {
        User member = userMapper.selectById(memberId);
        if (member == null || member.getTeamId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "成员不存在或未加入团队");
        }
        // owner 或 admin 可操作
        if (!teamService.isOwner(operatorId, member.getTeamId())
                && !teamService.isAdmin(operatorId, member.getTeamId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        Department newDept = departmentMapper.selectById(newDeptId);
        if (newDept == null || !newDept.getTeamId().equals(member.getTeamId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "目标部门不存在或不属于同一团队");
        }
        member.setDeptId(newDeptId);
        member.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(member);
    }

    @Override
    public List<UserVO> listMembers(Long deptId) {
        return userMapper.selectByDeptId(deptId).stream()
                .map(this::toUserVO)
                .collect(Collectors.toList());
    }

    private DepartmentVO toVO(Department dept) {
        DepartmentVO vo = new DepartmentVO();
        vo.setId(dept.getId());
        vo.setName(dept.getName());
        vo.setSortOrder(dept.getSortOrder());
        return vo;
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setTeamId(user.getTeamId());
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
