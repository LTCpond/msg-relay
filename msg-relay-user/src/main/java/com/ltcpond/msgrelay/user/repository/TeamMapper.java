package com.ltcpond.msgrelay.user.repository;

import com.ltcpond.msgrelay.user.model.entity.Team;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeamMapper {

    int insert(Team team);

    int updateById(Team team);

    Team selectById(@Param("id") Long id);

    List<Team> selectAll();

    int deleteByIdLogic(@Param("id") Long id);
}
