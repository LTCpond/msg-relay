package com.ltcpond.msgrelay.im.netty;

import com.ltcpond.msgrelay.common.auth.LoginSessionValidator;
import com.ltcpond.msgrelay.common.utils.JwtUtils;
import com.ltcpond.msgrelay.im.service.OnlineStatusService;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionAuthenticationTest {
    private JwtUtils jwt;
    private LoginSessionValidator sessions;
    private SessionManager manager;
    private OnlineStatusService presence;

    @BeforeEach
    void setup() {
        jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "secret", "test-secret-with-at-least-32-bytes-for-hmac");
        ReflectionTestUtils.setField(jwt, "accessTokenExpire", 7200L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpire", 604800L);
        sessions = mock(LoginSessionValidator.class);
        manager = new SessionManager();
        ReflectionTestUtils.setField(manager, "jwtUtils", jwt);
        ReflectionTestUtils.setField(manager, "loginSessionValidator", sessions);
        presence = mock(OnlineStatusService.class);
    }

    private EmbeddedChannel channel() {
        return new EmbeddedChannel(DefaultChannelId.newInstance(), new WebSocketHandler(manager, presence));
    }

    private String response(EmbeddedChannel channel) {
        TextWebSocketFrame frame = channel.readOutbound();
        try {
            return frame.text();
        } finally {
            frame.release();
        }
    }

    @Test
    void authUsesDeviceFromTokenAndRejectsRevokedReconnection() {
        when(sessions.isValid(1L, "PC")).thenReturn(true, false);
        String access = jwt.generateAccessToken(1L, "PC");
        var channel = channel();
        try {
            channel.writeInbound(new TextWebSocketFrame("AUTH " + access));
            assertEquals("AUTH_OK", response(channel));
            assertEquals("PC", channel.attr(SessionAttributes.DEVICE_ID).get());
            verify(presence).online(1L, "PC", channel.id().asLongText());
        } finally {
            channel.finishAndReleaseAll();
        }
        var reconnect = channel();
        try {
            reconnect.writeInbound(new TextWebSocketFrame("AUTH " + access));
            assertEquals("AUTH_FAIL", response(reconnect));
            assertFalse(reconnect.isActive());
            assertTrue(manager.getChannelIds(1L).isEmpty());
        } finally {
            reconnect.finishAndReleaseAll();
        }
    }

    @Test
    void rejectsRefreshAndLegacyAuthWithClientSuppliedDevice() {
        for (String payload : new String[] {jwt.generateRefreshToken(1L, "PC"),
                jwt.generateAccessToken(1L, "PC") + " fake-device"}) {
            var channel = channel();
            try {
                channel.writeInbound(new TextWebSocketFrame("AUTH " + payload));
                assertEquals("AUTH_FAIL", response(channel));
                assertFalse(channel.isActive());
            } finally {
                channel.finishAndReleaseAll();
            }
        }
        verifyNoInteractions(presence);
    }

    @Test
    void kickTargetsUserAndDeviceIncludingColonInDeviceId() {
        when(sessions.isValid(anyLong(), eq("PC:windows"))).thenReturn(true);
        var first = channel();
        var second = channel();
        try {
            first.writeInbound(new TextWebSocketFrame("AUTH " + jwt.generateAccessToken(1L, "PC:windows")));
            second.writeInbound(new TextWebSocketFrame("AUTH " + jwt.generateAccessToken(2L, "PC:windows")));
            assertEquals("AUTH_OK", response(first));
            assertEquals("AUTH_OK", response(second));
            var kick = new KickChannelHandler();
            ReflectionTestUtils.setField(kick, "sessionManager", manager);
            ReflectionTestUtils.setField(kick, "onlineStatusService", presence);
            Message message = mock(Message.class);
            when(message.getBody()).thenReturn("1:PC:windows".getBytes(StandardCharsets.UTF_8));
            kick.onMessage(message, null);
            assertEquals("KICKED", response(first));
            assertFalse(first.isActive());
            assertTrue(second.isActive());
            assertSame(second, manager.getChannelByDeviceId(2L, "PC:windows"));
        } finally {
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
        }
    }

    @Test
    void replacingSameDeviceConnectionPreservesNewMappingAndPresence() {
        when(sessions.isValid(1L, "PC")).thenReturn(true);
        var first = channel();
        var second = channel();
        try {
            first.writeInbound(new TextWebSocketFrame("AUTH " + jwt.generateAccessToken(1L, "PC")));
            assertEquals("AUTH_OK", response(first));
            second.writeInbound(new TextWebSocketFrame("AUTH " + jwt.generateAccessToken(1L, "PC")));
            assertEquals("AUTH_OK", response(second));
            assertFalse(first.isActive());
            assertSame(second, manager.getChannelByDeviceId(1L, "PC"));
            verify(presence, never()).offline(1L, "PC");
        } finally {
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
        }
    }
}
