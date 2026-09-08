package com.ltcpond.msgrelay.im.repository;

import com.ltcpond.msgrelay.im.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 用户会话投影 Mapper。 */
@Mapper
public interface ConversationMapper {

    int insert(Conversation conv);

    int updateById(Conversation conv);

    Conversation selectById(@Param("id") Long id);

    /** 根据用户和全局会话 ID 查询。 */
    Conversation selectByUserAndConversation(@Param("userId") Long userId,
                                               @Param("conversationId") Long conversationId);

    /** 查询用户的所有会话列表 */
    List<Conversation> selectByUserId(@Param("userId") Long userId);

    /** 原子递增未读数，last_msg_id < msgId 保证幂等 */
    int updateConversationByMsg(@Param("userId") Long userId,
                                 @Param("conversationId") Long conversationId,
                                 @Param("msgId") Long msgId);

    /** 唯一键上的原子 upsert，避免并发首条消息重复创建会话。 */
    int upsertByMessage(@Param("id") Long id,
                        @Param("userId") Long userId,
                        @Param("conversationId") Long conversationId,
                        @Param("msgId") Long msgId,
                        @Param("incrementUnread") boolean incrementUnread);

    int upsertMember(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("conversationId") Long conversationId);

    int deleteByUserAndConversation(@Param("userId") Long userId,
                                    @Param("conversationId") Long conversationId);

    int deleteByConversationId(@Param("conversationId") Long conversationId);

    List<Long> selectUserIdsByConversationId(@Param("conversationId") Long conversationId);

    int deleteByIdLogic(@Param("id") Long id);
}
