package com.ltcpond.msgrelay.user.chain.handler;

import com.ltcpond.msgrelay.common.constants.RedisChannel;
import com.ltcpond.msgrelay.user.chain.LoginContext;
import com.ltcpond.msgrelay.user.chain.LoginHandler;
import com.ltcpond.msgrelay.user.model.entity.LoginDevice;
import com.ltcpond.msgrelay.user.repository.LoginDeviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 多设备管理 — 责任链第三节点
 *
 * 限制单用户最多 5 个设备同时在线
 * 超出时踢出最早登录的设备（按 last_active_at 排序）
 * 被踢设备的 refreshToken 标记为逻辑删除，并通过 Redis Pub/Sub 通知所有节点断开连接
 */
@Slf4j
@Component
public class MultiDeviceHandler extends LoginHandler {

    @Resource
    private LoginDeviceMapper loginDeviceMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> kickRedisTemplate;

    @Override
    public boolean handle(LoginContext context) {
        Long count = loginDeviceMapper.countByUserId(context.getUser().getId());
        if (count >= 5) {
            // 找出最久未活跃的设备并踢下线
            List<LoginDevice> devices = loginDeviceMapper.selectByUserId(context.getUser().getId());
            if (!devices.isEmpty()) {
                LoginDevice oldest = devices.get(devices.size() - 1);

                // 1. 删除数据库记录
                loginDeviceMapper.deleteByIdLogic(oldest.getId());

                // 2. 发布 Redis 踢人消息（通知所有 Netty 节点）
                if (kickRedisTemplate != null) {
                    String kickMessage = context.getUser().getId() + ":" + oldest.getDeviceId();
                    kickRedisTemplate.convertAndSend(RedisChannel.KICK_CHANNEL, kickMessage);
                    log.info("Published kick message: userId={}, deviceId={}",
                            context.getUser().getId(), oldest.getDeviceId());
                }

                log.info("Kicked oldest device: userId={}, deviceId={}",
                        context.getUser().getId(), oldest.getDeviceId());
            }
        }
        return next(context);
    }
}
