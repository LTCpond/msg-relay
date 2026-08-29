package com.ltcpond.msgrelay.im.repository;

import com.ltcpond.msgrelay.im.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 消息 Mapper — 消息持久化与查询 */
@Mapper
public interface MessageMapper {

    int insert(Message message);

    int updateById(Message message);

    Message selectById(@Param("id") Long id);

    /** 根据全局 msgId 查询消息 */
    Message selectByMsgId(@Param("msgId") Long msgId);

    /** 查询历史消息 — 分页拉取，支持单聊和群聊 */
    List<Message> selectHistory(@Param("userId") Long userId, @Param("targetId") Long targetId,
                                @Param("receiverType") Integer receiverType,
                                @Param("beforeMsgId") Long beforeMsgId, @Param("limit") int limit);

    /** 查询群聊中用户未 ACK 的消息 — 用于标记已读时批量 ACK */
    List<Message> selectUnacknowledgedGroupMessages(@Param("groupId") Long groupId,
                                                     @Param("userId") Long userId,
                                                     @Param("afterTime") LocalDateTime afterTime);

    /** 查询超时未投递的消息 — 用于补偿任务 */
    List<Message> selectStaleMessages(@Param("status") int status,
                                       @Param("cutoff") String cutoff);
}
