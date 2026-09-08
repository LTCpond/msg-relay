package com.ltcpond.msgrelay.im.repository;

import com.ltcpond.msgrelay.im.model.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 全局会话主体 Mapper。 */
@Mapper
public interface ChatConversationMapper {

    int insert(ChatConversation conversation);

    ChatConversation selectById(@Param("id") Long id);

    ChatConversation selectDirect(@Param("directUserLow") Long directUserLow,
                                  @Param("directUserHigh") Long directUserHigh);

    ChatConversation selectByGroupId(@Param("groupId") Long groupId);

    int deleteByIdLogic(@Param("id") Long id);
}
