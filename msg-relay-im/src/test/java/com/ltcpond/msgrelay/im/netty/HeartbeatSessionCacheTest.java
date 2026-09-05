package com.ltcpond.msgrelay.im.netty;

import com.ltcpond.msgrelay.common.auth.LoginSessionValidator;
import com.ltcpond.msgrelay.im.config.HeartbeatConfig;
import com.ltcpond.msgrelay.im.service.OnlineStatusService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeartbeatSessionCacheTest {
    @Test
    void nextHeartbeatSeesSharedRevocationWithoutLocalPositiveCache() {
        var sessions = mock(LoginSessionValidator.class);
        when(sessions.isValid(1L, "PC")).thenReturn(true, false);
        var presence = mock(OnlineStatusService.class);
        var manager = mock(SessionManager.class);
        when(manager.getUserId(any())).thenReturn(1L);
        var handler = new HeartbeatHandler();
        ReflectionTestUtils.setField(handler, "heartbeatConfig", new HeartbeatConfig());
        ReflectionTestUtils.setField(handler, "onlineStatusService", presence);
        ReflectionTestUtils.setField(handler, "sessionManager", manager);
        ReflectionTestUtils.setField(handler, "loginSessionValidator", sessions);
        var channel = new EmbeddedChannel(handler);
        channel.attr(SessionAttributes.DEVICE_ID).set("PC");
        try {
            channel.pipeline().fireUserEventTriggered(new AuthenticationSuccessEvent());
            PingWebSocketFrame firstPing = channel.readOutbound();
            firstPing.release();
            channel.writeInbound(new PongWebSocketFrame());
            assertTrue(channel.isActive());
            verify(presence).heartbeat(1L, "PC");
            channel.pipeline().fireUserEventTriggered(IdleStateEvent.WRITER_IDLE_STATE_EVENT);
            PingWebSocketFrame secondPing = channel.readOutbound();
            secondPing.release();
            channel.writeInbound(new PongWebSocketFrame());
            TextWebSocketFrame kicked = channel.readOutbound();
            assertEquals("KICKED", kicked.text());
            kicked.release();
            assertFalse(channel.isActive());
            verify(sessions, times(2)).isValid(1L, "PC");
            verifyNoMoreInteractions(presence);
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
