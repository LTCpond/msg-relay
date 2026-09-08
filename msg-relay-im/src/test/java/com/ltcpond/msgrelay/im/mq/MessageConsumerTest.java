package com.ltcpond.msgrelay.im.mq;

import com.ltcpond.msgrelay.common.lock.LockAcquisitionException;
import com.ltcpond.msgrelay.common.lock.LockTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageConsumerTest {
    private RedisTemplate<String, Object> redis;
    private LockTemplate locks;
    private MessageConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(RedisTemplate.class);
        locks = mock(LockTemplate.class);
        consumer = new MessageConsumer();
        ReflectionTestUtils.setField(consumer, "redisTemplate", redis);
        ReflectionTestUtils.setField(consumer, "lockTemplate", locks);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lockAcquisitionFailureNeverDeletesAnotherConsumersLock() {
        when(redis.hasKey("mq:consume:done:1001")).thenReturn(false);
        when(locks.executeWithLock(anyString(), anyLong(), anyLong(), any(Supplier.class)))
                .thenThrow(new LockAcquisitionException("busy"));

        assertThrows(LockAcquisitionException.class,
                () -> consumer.onMessage("{\"msgId\":1001}"));
        verify(redis, never()).delete(anyString());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rechecksDoneMarkerAfterAcquiringProcessingLock() {
        when(redis.hasKey("mq:consume:done:1002")).thenReturn(false, true);
        when(locks.executeWithLock(anyString(), anyLong(), anyLong(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());

        assertDoesNotThrow(() -> consumer.onMessage("{\"msgId\":1002}"));
        verify(redis, times(2)).hasKey("mq:consume:done:1002");
    }
}
