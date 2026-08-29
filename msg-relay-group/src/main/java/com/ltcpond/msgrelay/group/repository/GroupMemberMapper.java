package com.ltcpond.msgrelay.group.repository;

import com.ltcpond.msgrelay.group.model.entity.GroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupMemberMapper {

    int insert(GroupMember member);

    int updateById(GroupMember member);

    GroupMember selectById(@Param("id") Long id);

    GroupMember selectByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    List<GroupMember> selectByGroupId(@Param("groupId") Long groupId);

    Long countByGroupId(@Param("groupId") Long groupId);

    int deleteByIdLogic(@Param("id") Long id);

    int deleteByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
