package com.ltcpond.msgrelay.user.service;

import com.ltcpond.msgrelay.user.model.dto.CreateMemberRequest;
import com.ltcpond.msgrelay.user.model.dto.CreateTeamRequest;
import com.ltcpond.msgrelay.user.model.entity.Team;
import com.ltcpond.msgrelay.user.model.entity.TeamAdmin;
import com.ltcpond.msgrelay.user.model.vo.UserVO;

import java.util.List;

public interface TeamService {

    Team create(Long ownerId, CreateTeamRequest request);

    Team getById(Long teamId);

    Team getByUserId(Long userId);

    void delete(Long teamId, Long userId);

    List<UserVO> listMembers(Long userId);

    /** 创建成员（owner/admin） */
    UserVO createMember(Long operatorId, CreateMemberRequest request);

    /** 开除员工（owner/admin） */
    void dismissMember(Long operatorId, Long memberId);

    /** 添加管理员（仅 owner） */
    void addAdmin(Long ownerId, Long userId);

    /** 移除管理员（仅 owner） */
    void removeAdmin(Long ownerId, Long userId);

    /** 管理员列表 */
    List<TeamAdmin> listAdmins(Long userId);

    /** 转让团队（仅 owner） */
    void transferOwnership(Long ownerId, Long newOwnerId);

    boolean isOwner(Long userId, Long teamId);
    boolean isAdmin(Long userId, Long teamId);
}
