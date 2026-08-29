package com.ltcpond.msgrelay.user.repository;

import com.ltcpond.msgrelay.user.model.entity.TeamAdmin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 团队管理员 Mapper */
@Mapper
public interface TeamAdminMapper {

    int insert(TeamAdmin admin);

    int deleteByTeamAndUser(@Param("teamId") Long teamId, @Param("userId") Long userId);

    List<TeamAdmin> selectByTeamId(@Param("teamId") Long teamId);

    /** 判断用户是否为指定团队的管理员 */
    boolean existsByTeamAndUser(@Param("teamId") Long teamId, @Param("userId") Long userId);
}
