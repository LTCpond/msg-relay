package com.ltcpond.msgrelay.group.repository;

import com.ltcpond.msgrelay.group.model.entity.GroupMemberIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupMemberIndexMapper {

    int insert(GroupMemberIndex record);

    int updateStatus(@Param("groupId") Long groupId,
                     @Param("userId") Long userId,
                     @Param("status") Integer status);

    int updateLeftAt(@Param("groupId") Long groupId,
                     @Param("userId") Long userId);

    int updateJoinedAt(@Param("groupId") Long groupId,
                       @Param("userId") Long userId);

    GroupMemberIndex selectByGroupAndUser(@Param("groupId") Long groupId,
                                          @Param("userId") Long userId);

    int ensureSequence(@Param("groupId") Long groupId);

    int allocateNextIndex(@Param("groupId") Long groupId);

    Integer selectLastInsertId();

    List<GroupMemberIndex> selectByGroupId(@Param("groupId") Long groupId);

    List<GroupMemberIndex> selectActiveByGroupId(@Param("groupId") Long groupId);
}
