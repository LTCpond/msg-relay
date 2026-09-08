package com.ltcpond.msgrelay.im.service;

import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.group.model.entity.GroupMember;
import com.ltcpond.msgrelay.group.repository.GroupMemberMapper;
import com.ltcpond.msgrelay.im.model.entity.ChatConversation;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import com.ltcpond.msgrelay.im.repository.ChatConversationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageAccessServiceTest {
    private GroupMemberMapper groupMembers;
    private ChatConversationMapper conversations;
    private MultiLevelCache cache;
    private MessageAccessService service;

    @BeforeEach
    void setUp() {
        groupMembers = mock(GroupMemberMapper.class);
        conversations = mock(ChatConversationMapper.class);
        cache = mock(MultiLevelCache.class);
        service = new MessageAccessService();
        ReflectionTestUtils.setField(service, "groupMemberMapper", groupMembers);
        ReflectionTestUtils.setField(service, "conversationMapper", conversations);
        ReflectionTestUtils.setField(service, "cache", cache);
    }

    @Test
    void groupConversationRequiresActiveMembership() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(100L);
        conversation.setType(ReceiverType.GROUP.getCode());
        conversation.setGroupId(99L);
        when(cache.get(org.mockito.ArgumentMatchers.eq("conversation:100"),
                org.mockito.ArgumentMatchers.eq(ChatConversation.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1800L)))
                .thenReturn(conversation);
        when(groupMembers.selectByGroupIdAndUserId(99L, 1L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.assertCanAccessConversation(1L, 100L));

        when(groupMembers.selectByGroupIdAndUserId(99L, 1L)).thenReturn(new GroupMember());
        assertDoesNotThrow(
                () -> service.assertCanAccessConversation(1L, 100L));
    }

    @Test
    void directMessageRejectsThirdPartyAndSenderAck() {
        Message message = new Message();
        message.setSenderId(1L);
        message.setConversationId(200L);
        ChatConversation conversation = new ChatConversation();
        conversation.setId(200L);
        conversation.setType(ReceiverType.SINGLE.getCode());
        conversation.setDirectUserLow(1L);
        conversation.setDirectUserHigh(2L);
        when(cache.get(org.mockito.ArgumentMatchers.eq("conversation:200"),
                org.mockito.ArgumentMatchers.eq(ChatConversation.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1800L)))
                .thenReturn(conversation);

        assertThrows(BusinessException.class, () -> service.assertCanAccessMessage(3L, message));
        assertThrows(BusinessException.class, () -> service.assertCanAcknowledge(1L, message));
        assertDoesNotThrow(() -> service.assertCanAcknowledge(2L, message));
    }
}
