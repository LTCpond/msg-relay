package com.ltcpond.msgrelay.user.repository;

import com.ltcpond.msgrelay.user.model.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DepartmentMapper {

    int insert(Department dept);

    int updateById(Department dept);

    Department selectById(@Param("id") Long id);

    List<Department> selectByTeamId(@Param("teamId") Long teamId);

    int deleteByIdLogic(@Param("id") Long id);
}
