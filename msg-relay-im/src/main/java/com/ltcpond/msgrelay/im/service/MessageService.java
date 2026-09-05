package com.ltcpond.msgrelay.im.service;

import com.ltcpond.msgrelay.im.model.dto.SendMessageRequest;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.vo.MessageVO;
import com.ltcpond.msgrelay.im.model.vo.ReadStatusVO;

import java.util.List;

/** 消息服务接口 — IM 核心 */
public interface MessageService {

    /** 发送消息 */
    MessageVO send(Long senderId, SendMessageRequest request);

    /** 撤回消息 — 仅发送者可撤回，2 小时内有效 */
    void recall(Long userId, Long msgId);

    /** 根据全局 msgId 查询消息（返回 VO） */
    MessageVO getByMsgId(Long msgId);

    /** 根据全局 msgId 查询消息（返回实体） */
    Message getByMsgIdEntity(Long msgId);

    /** 标记单聊消息为已投递 */
    void markDelivered(Long msgId);

    /** 标记单聊消息为已读 */
    void markRead(Long msgId);

    /** 查询历史消息 — 分页拉取，过滤隐藏消息，支持单聊和群聊 */
    List<MessageVO> queryHistory(Long userId, Long targetId, Integer receiverType, Long beforeMsgId, int limit);

    /** 隐藏消息 — 仅对当前用户不可见 */
    void hideMessage(Long userId, Long msgId);

    /** 查询消息已读状态 — 群聊展示已读/未读成员列表 */
    ReadStatusVO getReadStatus(Long userId, Long msgId);
}
