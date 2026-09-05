package com.ltcpond.msgrelay.common.cache;

import com.ltcpond.msgrelay.common.lock.LockTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MultiLevelCacheTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void loaderFailureIsNeverExecutedTwice() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("test:key")).thenReturn(null);

        LockTemplate locks = mock(LockTemplate.class);
        when(locks.executeWithLock(anyString(), anyLong(), anyLong(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());

        MultiLevelCache cache = new MultiLevelCache();
        ReflectionTestUtils.setField(cache, "redisTemplate", redis);
        ReflectionTestUtils.setField(cache, "lockTemplate", locks);
        ReflectionTestUtils.setField(cache, "stringRedisTemplate", mock(StringRedisTemplate.class));

        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> cache.get("test:key", String.class, key -> {
            calls.incrementAndGet();
            throw new IllegalStateException("db failed");
        }, 60));
        assertEquals(1, calls.get());
    }
}
