package com.ltcpond.msgrelay.im.service;

import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import com.ltcpond.msgrelay.im.model.entity.ChatConversation;
import com.ltcpond.msgrelay.im.model.entity.Conversation;
import com.ltcpond.msgrelay.im.model.enums.ReceiverType;
import com.ltcpond.msgrelay.im.repository.ChatConversationMapper;
import com.ltcpond.msgrelay.im.repository.ConversationMapper;
import com.ltcpond.msgrelay.im.service.impl.ConversationServiceImpl;
import com.ltcpond.msgrelay.user.model.vo.UserVO;
import com.ltcpond.msgrelay.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ConversationServiceImplTest {

    private ChatConversationMapper chats;
    private ConversationMapper userConversations;
    private SnowflakeIdGenerator ids;
    private ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        chats = mock(ChatConversationMapper.class);
        userConversations = mock(ConversationMapper.class);
        ids = mock(SnowflakeIdGenerator.class);
        service = new ConversationServiceImpl();
        ReflectionTestUtils.setField(service, "chatConversationMapper", chats);
        ReflectionTestUtils.setField(service, "conversationMapper", userConversations);
        ReflectionTestUtils.setField(service, "idGenerator", ids);
        ReflectionTestUtils.setField(service, "cache", mock(MultiLevelCache.class));
        ReflectionTestUtils.setField(service, "userService", mock(UserService.class));
    }

    @Test
    void normalizesDirectParticipantsAndCreatesBothUserProjections() {
        ChatConversation stored = directConversation(900L, 10L, 20L);
        Conversation view = new Conversation();
        view.setConversationId(900L);
        when(chats.selectDirect(10L, 20L)).thenReturn(null, stored);
        when(ids.nextId()).thenReturn(900L, 901L, 902L);
        when(userConversations.selectByUserAndConversation(20L, 900L)).thenReturn(view);

        Conversation result = service.createDirectConversation(20L, 10L);

        ArgumentCaptor<ChatConversation> inserted = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chats).insert(inserted.capture());
        assertEquals(10L, inserted.getValue().getDirectUserLow());
        assertEquals(20L, inserted.getValue().getDirectUserHigh());
        assertEquals(ReceiverType.SINGLE.getCode(), inserted.getValue().getType());
        verify(userConversations).upsertMember(901L, 20L, 900L);
        verify(userConversations).upsertMember(902L, 10L, 900L);
        assertSame(view, result);
    }

    @Test
    void reusesExistingDirectConversationForReverseDirection() {
        ChatConversation stored = directConversation(900L, 10L, 20L);
        Conversation view = new Conversation();
        view.setConversationId(900L);
        when(chats.selectDirect(10L, 20L)).thenReturn(stored);
        when(ids.nextId()).thenReturn(901L, 902L);
        when(userConversations.selectByUserAndConversation(10L, 900L)).thenReturn(view);

        Conversation result = service.createDirectConversation(10L, 20L);

        verify(chats, never()).insert(any());
        verify(userConversations).upsertMember(anyLong(), eq(10L), eq(900L));
        verify(userConversations).upsertMember(anyLong(), eq(20L), eq(900L));
        assertSame(view, result);
    }

    private static ChatConversation directConversation(Long id, Long low, Long high) {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(id);
        conversation.setType(ReceiverType.SINGLE.getCode());
        conversation.setDirectUserLow(low);
        conversation.setDirectUserHigh(high);
        return conversation;
    }
}
