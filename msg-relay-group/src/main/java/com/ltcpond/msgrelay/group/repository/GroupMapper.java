package com.ltcpond.msgrelay.group.repository;

import com.ltcpond.msgrelay.group.model.entity.Group;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupMapper {

    int insert(Group group);

    int updateById(Group group);

    Group selectById(@Param("id") Long id);

    int deleteByIdLogic(@Param("id") Long id);
}
