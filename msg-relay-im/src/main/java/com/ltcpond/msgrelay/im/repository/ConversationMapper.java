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
                                        @Param("targetId") Long targetId,
                                        @Param("targetType") Integer targetType);

    /** 查询用户的所有会话列表 */
    List<Conversation> selectByUserId(@Param("userId") Long userId);

    /** 原子递增未读数，last_msg_id < msgId 保证幂等 */
    int updateConversationByMsg(@Param("userId") Long userId,
                                 @Param("targetId") Long targetId,
                                 @Param("targetType") Integer targetType,
                                 @Param("msgId") Long msgId);

    /** 唯一键上的原子 upsert，避免并发首条消息重复创建会话。 */
    int upsertByMessage(@Param("id") Long id,
                        @Param("userId") Long userId,
                        @Param("targetId") Long targetId,
                        @Param("targetType") Integer targetType,
                        @Param("msgId") Long msgId,
                        @Param("incrementUnread") boolean incrementUnread);

    int deleteByIdLogic(@Param("id") Long id);
}
