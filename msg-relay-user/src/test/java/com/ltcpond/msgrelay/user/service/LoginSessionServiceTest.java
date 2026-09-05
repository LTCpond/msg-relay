package com.ltcpond.msgrelay.user.service;

import com.ltcpond.msgrelay.common.constants.RedisChannel;
import com.ltcpond.msgrelay.user.controller.AuthController;
import com.ltcpond.msgrelay.user.repository.LoginDeviceMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginSessionServiceTest {
    @Test
    void invalidationPrecedesCommitAndKickOnlyFollowsCommit() {
        var mapper = mock(LoginDeviceMapper.class);
        var redis = mock(StringRedisTemplate.class);
        var cache = mock(LoginSessionCache.class);
        var service = new LoginSessionService(mapper, redis, cache);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.revoke(1L, "PC:windows");
            verify(mapper).deleteByUserIdAndDeviceId(1L, "PC:windows");
            verifyNoInteractions(redis, cache);
            var sync = TransactionSynchronizationManager.getSynchronizations().get(0);
            sync.beforeCommit(false);
            verify(cache).beginChange(eq(1L), eq("PC:windows"), startsWith("UPDATING:"));
            verifyNoInteractions(redis);
            sync.afterCommit();
            verify(redis).convertAndSend(RedisChannel.KICK_CHANNEL, "1:PC:windows");
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            verify(cache).endChange(eq(1L), eq("PC:windows"), startsWith("UPDATING:"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void failedRedisInvalidationRollsBackDatabaseRevocation() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE device (id BIGINT PRIMARY KEY, deleted INT)");
        jdbc.update("INSERT INTO device VALUES (1, 0)");
        var mapper = mock(LoginDeviceMapper.class);
        when(mapper.deleteByUserIdAndDeviceId(1L, "PC")).thenAnswer(invocation ->
                jdbc.update("UPDATE device SET deleted = 1 WHERE id = 1"));
        var redis = mock(StringRedisTemplate.class);
        var cache = mock(LoginSessionCache.class);
        doThrow(new RedisConnectionFailureException("offline")).when(cache).beginChange(eq(1L), eq("PC"), anyString());
        var service = new LoginSessionService(mapper, redis, cache);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThrows(RedisConnectionFailureException.class, () ->
                transaction.executeWithoutResult(status -> service.revoke(1L, "PC")));
        assertEquals(0, jdbc.queryForObject("SELECT deleted FROM device WHERE id = 1", Integer.class));
        verifyNoInteractions(redis);
    }

    @Test
    void loginInvalidatesNegativeCacheAndRollbackDoesNotPublishKick() {
        var cache = mock(LoginSessionCache.class);
        var redis = mock(StringRedisTemplate.class);
        var service = new LoginSessionService(mock(LoginDeviceMapper.class), redis, cache);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.sessionChanged(1L, "PC");
            var sync = TransactionSynchronizationManager.getSynchronizations().get(0);
            sync.beforeCommit(false);
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            var order = inOrder(cache);
            order.verify(cache).beginChange(eq(1L), eq("PC"), anyString());
            order.verify(cache).endChange(eq(1L), eq("PC"), anyString());
            verifyNoInteractions(redis);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void unknownCommitOutcomeKeepsDatabaseFallbackMarker() {
        var cache = mock(LoginSessionCache.class);
        var service = new LoginSessionService(mock(LoginDeviceMapper.class), mock(StringRedisTemplate.class), cache);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.sessionChanged(1L, "PC");
            var sync = TransactionSynchronizationManager.getSynchronizations().get(0);
            sync.beforeCommit(false);
            sync.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
            verify(cache, never()).endChange(anyLong(), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void logoutUsesVerifiedIdentityInsteadOfClientDeviceParameter() {
        var auth = mock(AuthService.class);
        var controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", auth);
        var request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        request.setAttribute("deviceId", "own-device");
        request.setParameter("deviceId", "another-device");
        controller.logout(request);
        verify(auth).logout(1L, "own-device");
    }

    @Test
    void activeTransactionBypassesSharedCache() {
        var mapper = mock(LoginDeviceMapper.class);
        var cache = mock(LoginSessionCache.class);
        var service = new LoginSessionService(mapper, mock(StringRedisTemplate.class), cache);
        when(mapper.existsByUserIdAndDeviceId(1L, "PC")).thenReturn(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertTrue(service.isValid(1L, "PC"));
            verifyNoInteractions(cache);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
