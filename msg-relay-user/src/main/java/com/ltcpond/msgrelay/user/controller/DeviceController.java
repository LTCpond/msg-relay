package com.ltcpond.msgrelay.user.controller;

import com.ltcpond.msgrelay.common.constants.RedisChannel;
import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.user.model.entity.LoginDevice;
import com.ltcpond.msgrelay.user.repository.LoginDeviceMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备管理控制器 — 查询已登录设备、踢出指定设备
 */
@RestController
@RequestMapping("/api/user/device")
public class DeviceController {

    @Resource
    private LoginDeviceMapper loginDeviceMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> kickRedisTemplate;

    /** 查询当前用户的所有登录设备 */
    @GetMapping("/list")
    public Result<List<LoginDevice>> listDevices(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<LoginDevice> devices = loginDeviceMapper.selectByUserId(userId);
        return Result.ok(devices);
    }

    /** 踢掉指定设备 */
    @DeleteMapping("/{deviceId}")
    public Result<Void> kickDevice(@PathVariable String deviceId,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        // 1. 删除数据库记录
        loginDeviceMapper.deleteByUserIdAndDeviceId(userId, deviceId);

        // 2. 发布 Redis 踢人消息（通知所有 Netty 节点断开连接）
        if (kickRedisTemplate != null) {
            String kickMessage = userId + ":" + deviceId;
            kickRedisTemplate.convertAndSend(RedisChannel.KICK_CHANNEL, kickMessage);
        }

        return Result.ok();
    }
}
