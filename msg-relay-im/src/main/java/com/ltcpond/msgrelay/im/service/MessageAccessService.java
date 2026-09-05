package com.ltcpond.msgrelay.im.service;

import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.group.repository.GroupMemberMapper;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** 历史、搜索、ACK、已读状态共用的消息/会话授权入口。 */
@Service
public class MessageAccessService {

    @Resource
    private GroupMemberMapper groupMemberMapper;

    public void assertCanAccessConversation(Long userId, Long targetId, Integer receiverType) {
        if (userId == null || targetId == null || receiverType == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该会话");
        }
        if (receiverType == ReceiverType.GROUP.getCode()
                && groupMemberMapper.selectByGroupIdAndUserId(targetId, userId) == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "你不是该群成员");
        }
        if (receiverType != ReceiverType.SINGLE.getCode()
                && receiverType != ReceiverType.GROUP.getCode()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的会话类型");
        }
    }

    public void assertCanAccessMessage(Long userId, Message message) {
        if (message == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "消息不存在");
        }
        if (userId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该消息");
        }
        if (message.getReceiverType() == ReceiverType.GROUP.getCode()) {
            assertCanAccessConversation(userId, message.getReceiverId(), message.getReceiverType());
            return;
        }
        if (!userId.equals(message.getSenderId()) && !userId.equals(message.getReceiverId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该消息");
        }
    }

    public void assertCanAcknowledge(Long userId, Message message) {
        assertCanAccessMessage(userId, message);
        if (message.getReceiverType() == ReceiverType.SINGLE.getCode()
                && !userId.equals(message.getReceiverId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有消息接收者可以确认 ACK");
        }
        if (message.getReceiverType() == ReceiverType.GROUP.getCode()
                && userId.equals(message.getSenderId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "发送者不能确认自己的群消息");
        }
    }
}
