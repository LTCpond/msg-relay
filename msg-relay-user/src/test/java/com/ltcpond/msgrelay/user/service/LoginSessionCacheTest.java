package com.ltcpond.msgrelay.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginSessionCacheTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private LoginSessionCache cache;
    private BooleanSupplier database;
    private static final String KEY = "login:session:1:PC";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        cache = new LoginSessionCache(redis);
        database = mock(BooleanSupplier.class);
    }

    @Test
    void cacheHitsNeverQueryMySql() {
        when(values.get(KEY)).thenReturn("1", "0");
        assertTrue(cache.isValid(1L, "PC", database));
        assertFalse(cache.isValid(1L, "PC", database));
        verifyNoInteractions(database);
        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void cacheMissUsesDifferentPositiveAndNegativeLifetimes() {
        when(values.setIfAbsent(eq(KEY), startsWith("LOADING:"), eq(Duration.ofSeconds(10)))).thenReturn(true);
        when(database.getAsBoolean()).thenReturn(true, false);
        assertTrue(cache.isValid(1L, "PC", database));
        assertFalse(cache.isValid(1L, "PC", database));
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), startsWith("LOADING:"), eq("1"), eq("300"));
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), startsWith("LOADING:"), eq("0"), eq("30"));
    }

    @Test
    void updatingOrLoadingStatesOnlyReadDatabaseWithoutRepopulating() {
        when(values.get(KEY)).thenReturn("UPDATING:transaction", "LOADING:other-request");
        when(database.getAsBoolean()).thenReturn(false, true);
        assertFalse(cache.isValid(1L, "PC", database));
        assertTrue(cache.isValid(1L, "PC", database));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void losingLoadOwnershipNeverWritesAStaleResult() {
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(false);
        when(database.getAsBoolean()).thenReturn(true);
        assertTrue(cache.isValid(1L, "PC", database));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void redisReadFailureFallsBackToAuthoritativeDatabase() {
        when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("offline"));
        when(database.getAsBoolean()).thenReturn(true, false);
        assertTrue(cache.isValid(1L, "PC", database));
        assertFalse(cache.isValid(1L, "PC", database));
        verify(database, times(2)).getAsBoolean();
    }

    @Test
    void databaseErrorsAreNotCachedAsRevocation() {
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(true);
        when(database.getAsBoolean()).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> cache.isValid(1L, "PC", database));
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), startsWith("LOADING:"));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void failedInvalidationIsPropagatedButCleanupFailureKeepsDatabaseFallback() {
        doThrow(new RedisConnectionFailureException("offline")).when(values).set(KEY, "UPDATING:tx");
        assertThrows(RedisConnectionFailureException.class, () -> cache.beginChange(1L, "PC", "UPDATING:tx"));
        when(redis.execute(any(RedisScript.class), eq(List.of(KEY)), eq("UPDATING:tx")))
                .thenThrow(new RedisConnectionFailureException("offline"));
        assertDoesNotThrow(() -> cache.endChange(1L, "PC", "UPDATING:tx"));
    }
}
