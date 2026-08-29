package com.ltcpond.msgrelay.user.service.impl;
import com.ltcpond.msgrelay.user.service.TeamService;

import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import com.ltcpond.msgrelay.user.model.dto.CreateMemberRequest;
import com.ltcpond.msgrelay.user.model.dto.CreateTeamRequest;
import com.ltcpond.msgrelay.user.model.entity.Department;
import com.ltcpond.msgrelay.user.model.entity.Team;
import com.ltcpond.msgrelay.user.model.entity.TeamAdmin;
import com.ltcpond.msgrelay.user.model.entity.User;
import com.ltcpond.msgrelay.user.model.vo.UserVO;
import com.ltcpond.msgrelay.user.repository.DepartmentMapper;
import com.ltcpond.msgrelay.user.repository.TeamAdminMapper;
import com.ltcpond.msgrelay.user.repository.TeamMapper;
import com.ltcpond.msgrelay.user.repository.UserMapper;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeamServiceImpl implements TeamService {

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamAdminMapper teamAdminMapper;

    @Resource
    private DepartmentMapper departmentMapper;

    @Resource
    private SnowflakeIdGenerator idGenerator;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Team create(Long ownerId, CreateTeamRequest request) {
        Team team = new Team();
        team.setId(idGenerator.nextId());
        team.setName(request.getName());
        team.setLogo(request.getLogo());
        team.setOwnerId(ownerId);
        team.setMaxMembers(request.getMaxMembers() != null ? request.getMaxMembers() : 200);
        team.setDeleted(0);
        team.setCreatedAt(LocalDateTime.now());
        team.setUpdatedAt(LocalDateTime.now());
        teamMapper.insert(team);

        User owner = userMapper.selectById(ownerId);
        if (owner != null) {
            owner.setTeamId(team.getId());
            userMapper.updateById(owner);
        }

        return team;
    }

    @Override
    public Team getById(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "团队不存在");
        }
        return team;
    }

    @Override
    public Team getByUserId(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getTeamId() == null) {
            return null;
        }
        return teamMapper.selectById(user.getTeamId());
    }

    @Override
    public void delete(Long teamId, Long userId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "团队不存在");
        }
        if (!team.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有团队所有者才能删除团队");
        }
        teamMapper.deleteByIdLogic(teamId);
    }

    @Override
    public List<UserVO> listMembers(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getTeamId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户未加入任何团队");
        }
        return userMapper.selectByTeamId(user.getTeamId()).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public UserVO createMember(Long operatorId, CreateMemberRequest request) {
        User operator = userMapper.selectById(operatorId);
        Long teamId = getOperatorTeamId(operator);
        verifyAdminOrOwner(operatorId, teamId);

        if (userMapper.countByUsername(request.getUsername()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        if (userMapper.countByJobNumberAndTeamId(request.getJobNumber(), teamId) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该企业内工号已存在");
        }
        Department dept = departmentMapper.selectById(request.getDeptId());
        if (dept == null || !dept.getTeamId().equals(teamId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在或不属于该团队");
        }

        User user = new User();
        user.setId(idGenerator.nextId());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setJobNumber(request.getJobNumber());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setTeamId(teamId);
        user.setDeptId(request.getDeptId());
        user.setStatus(User.STATUS_NORMAL);
        user.setDeleted(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        return toVO(user);
    }

    @Override
    public void dismissMember(Long operatorId, Long memberId) {
        User operator = userMapper.selectById(operatorId);
        Long teamId = getOperatorTeamId(operator);
        verifyAdminOrOwner(operatorId, teamId);

        User member = userMapper.selectById(memberId);
        if (member == null || !teamId.equals(member.getTeamId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该成员不属于当前团队");
        }
        Team team = teamMapper.selectById(teamId);
        if (team != null && team.getOwnerId().equals(memberId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能开除团队所有者");
        }

        member.setTeamId(null);
        member.setDeptId(null);
        member.setJobNumber(null);
        member.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(member);
    }

    @Override
    public void addAdmin(Long ownerId, Long userId) {
        User owner = userMapper.selectById(ownerId);
        Long teamId = getOperatorTeamId(owner);
        if (!isOwner(ownerId, teamId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有团队所有者才能管理管理员");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !teamId.equals(user.getTeamId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该用户不属于当前团队");
        }
        if (teamAdminMapper.existsByTeamAndUser(teamId, userId)) {
            throw new BusinessException(ResultCode.CONFLICT, "该用户已是管理员");
        }
        TeamAdmin admin = new TeamAdmin();
        admin.setId(idGenerator.nextId());
        admin.setTeamId(teamId);
        admin.setUserId(userId);
        teamAdminMapper.insert(admin);
    }

    @Override
    public void removeAdmin(Long ownerId, Long userId) {
        User owner = userMapper.selectById(ownerId);
        Long teamId = getOperatorTeamId(owner);
        if (!isOwner(ownerId, teamId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有团队所有者才能管理管理员");
        }
        teamAdminMapper.deleteByTeamAndUser(teamId, userId);
    }

    @Override
    public List<TeamAdmin> listAdmins(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getTeamId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户未加入任何团队");
        }
        return teamAdminMapper.selectByTeamId(user.getTeamId());
    }

    @Override
    public void transferOwnership(Long ownerId, Long newOwnerId) {
        User owner = userMapper.selectById(ownerId);
        Long teamId = getOperatorTeamId(owner);
        if (!isOwner(ownerId, teamId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有团队所有者才能转让");
        }
        User newOwner = userMapper.selectById(newOwnerId);
        if (newOwner == null || !teamId.equals(newOwner.getTeamId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "接收者不属于当前团队");
        }
        Team team = teamMapper.selectById(teamId);
        team.setOwnerId(newOwnerId);
        team.setUpdatedAt(LocalDateTime.now());
        teamMapper.updateById(team);
    }

    @Override
    public boolean isOwner(Long userId, Long teamId) {
        Team team = teamMapper.selectById(teamId);
        return team != null && userId.equals(team.getOwnerId());
    }

    @Override
    public boolean isAdmin(Long userId, Long teamId) {
        return teamAdminMapper.existsByTeamAndUser(teamId, userId);
    }

    private Long getOperatorTeamId(User operator) {
        if (operator == null || operator.getTeamId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户未加入任何团队");
        }
        return operator.getTeamId();
    }

    private void verifyAdminOrOwner(Long operatorId, Long teamId) {
        if (!isOwner(operatorId, teamId) && !isAdmin(operatorId, teamId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有团队所有者或管理员才能操作");
        }
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setJobNumber(user.getJobNumber());
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
