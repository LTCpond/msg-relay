package com.ltcpond.msgrelay.user.service;

import com.ltcpond.msgrelay.common.auth.LoginSessionValidator;
import com.ltcpond.msgrelay.common.constants.RedisChannel;
import com.ltcpond.msgrelay.user.repository.LoginDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** MySQL 管理登录状态；Pub/Sub 通知各节点关闭连接并清理 Presence。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginSessionService implements LoginSessionValidator {
    private final LoginDeviceMapper loginDeviceMapper;
    private final StringRedisTemplate redisTemplate;
    private final LoginSessionCache sessionCache;

    @Override
    public boolean isValid(Long userId, String deviceId) {
        if (userId == null || !StringUtils.hasText(deviceId)) {
            return false;
        }
        // 事务内可能读到未提交数据或旧快照，不能将结果共享到 Redis。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return loginDeviceMapper.existsByUserIdAndDeviceId(userId, deviceId);
        }
        return sessionCache.isValid(userId, deviceId,
                () -> loginDeviceMapper.existsByUserIdAndDeviceId(userId, deviceId));
    }

    @Transactional
    public void revoke(Long userId, String deviceId) {
        loginDeviceMapper.deleteByUserIdAndDeviceId(userId, deviceId);
        synchronizeCache(userId, deviceId, true);
    }

    /** 登录创建/恢复设备记录后调用，与设备写入共享同一个事务。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sessionChanged(Long userId, String deviceId) {
        synchronizeCache(userId, deviceId, false);
    }

    private void synchronizeCache(Long userId, String deviceId, boolean kick) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("设备会话变更必须在事务中执行");
        }
        String marker = "UPDATING:" + UUID.randomUUID();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                sessionCache.beginChange(userId, deviceId, marker);
            }

            @Override
            public void afterCommit() {
                if (kick) {
                    publishKick(userId, deviceId);
                }
            }

            @Override
            public void afterCompletion(int status) {
                // UNKNOWN 时保留标记：只回源，不共享可能尚未最终提交的状态。
                if (status != STATUS_UNKNOWN) {
                    sessionCache.endChange(userId, deviceId, marker);
                }
            }
        });
    }

    private void publishKick(Long userId, String deviceId) {
        try {
            // 订阅端读取原始字符串，不能使用 JSON value serializer。
            redisTemplate.convertAndSend(RedisChannel.KICK_CHANNEL, userId + ":" + deviceId);
        } catch (RuntimeException e) {
            // 会话已撤销，HTTP/刷新立即拒绝；心跳检查断开漏收通知的连接。
            log.error("发布踢人通知失败，等待心跳校验: userId={}, deviceId={}", userId, deviceId, e);
        }
    }
}
