package com.ltcpond.msgrelay.im.service;

import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.group.model.entity.GroupMember;
import com.ltcpond.msgrelay.group.repository.GroupMemberMapper;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageAccessServiceTest {
    private GroupMemberMapper groupMembers;
    private MessageAccessService service;

    @BeforeEach
    void setUp() {
        groupMembers = mock(GroupMemberMapper.class);
        service = new MessageAccessService();
        ReflectionTestUtils.setField(service, "groupMemberMapper", groupMembers);
    }

    @Test
    void groupConversationRequiresActiveMembership() {
        when(groupMembers.selectByGroupIdAndUserId(99L, 1L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.assertCanAccessConversation(1L, 99L, ReceiverType.GROUP.getCode()));

        when(groupMembers.selectByGroupIdAndUserId(99L, 1L)).thenReturn(new GroupMember());
        assertDoesNotThrow(
                () -> service.assertCanAccessConversation(1L, 99L, ReceiverType.GROUP.getCode()));
    }

    @Test
    void directMessageRejectsThirdPartyAndSenderAck() {
        Message message = new Message();
        message.setSenderId(1L);
        message.setReceiverId(2L);
        message.setReceiverType(ReceiverType.SINGLE.getCode());

        assertThrows(BusinessException.class, () -> service.assertCanAccessMessage(3L, message));
        assertThrows(BusinessException.class, () -> service.assertCanAcknowledge(1L, message));
        assertDoesNotThrow(() -> service.assertCanAcknowledge(2L, message));
    }
}
