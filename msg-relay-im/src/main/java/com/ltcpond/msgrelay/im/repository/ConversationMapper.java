package com.ltcpond.msgrelay.im.repository;

import com.ltcpond.msgrelay.im.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 会话 Mapper — 会话 CRUD */
@Mapper
public interface ConversationMapper {

    int insert(Conversation conv);

    int updateById(Conversation conv);

    Conversation selectById(@Param("id") Long id);

    /** 根据用户和目标查询唯一会话 */
    Conversation selectByUserAndTarget(@Param("userId") Long userId,
                                        @Param("targetId") Long targetId);

    /** 查询用户的所有会话列表 */
    List<Conversation> selectByUserId(@Param("userId") Long userId);

    /** 原子递增未读数，last_msg_id < msgId 保证幂等 */
    int updateConversationByMsg(@Param("userId") Long userId,
                                 @Param("targetId") Long targetId,
                                 @Param("msgId") Long msgId);

    int deleteByIdLogic(@Param("id") Long id);
}
