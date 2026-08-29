package com.ltcpond.msgrelay.user.service.impl;
import com.ltcpond.msgrelay.user.service.AuthService;

import com.ltcpond.msgrelay.user.model.dto.LoginRequest;
import com.ltcpond.msgrelay.user.model.dto.LoginResponse;
import com.ltcpond.msgrelay.user.model.entity.LoginDevice;
import com.ltcpond.msgrelay.user.model.entity.User;
import com.ltcpond.msgrelay.user.repository.LoginDeviceMapper;
import com.ltcpond.msgrelay.user.repository.UserMapper;
import com.ltcpond.msgrelay.user.chain.LoginContext;
import com.ltcpond.msgrelay.user.chain.LoginHandler;
import com.ltcpond.msgrelay.user.chain.handler.PasswordCheckHandler;
import com.ltcpond.msgrelay.user.chain.handler.BanCheckHandler;
import com.ltcpond.msgrelay.user.chain.handler.MultiDeviceHandler;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.common.utils.JwtUtils;
import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 认证服务 — 用户登录/登出/刷新Token
 *
 * 登录流程:
 * 1. 构建 LoginContext，传入责任链
 * 2. PasswordCheck → BanCheck → MultiDevice 依次校验
 * 3. 通过后生成双 token，记录设备到 t_login_device
 *
 * 双 Token 设计:
 * - accessToken (2h): 随 API 请求携带，短期有效减少泄露风险
 * - refreshToken (7d): 存储在设备记录中，用于无感续期
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private LoginDeviceMapper loginDeviceMapper;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private SnowflakeIdGenerator idGenerator;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Resource
    private PasswordCheckHandler passwordCheckHandler;

    @Resource
    private BanCheckHandler banCheckHandler;

    @Resource
    private MultiDeviceHandler multiDeviceHandler;

    @Override
    public LoginResponse login(LoginRequest request, String ip) {
        LoginContext context = new LoginContext();
        context.setUsername(request.getUsername());
        context.setPassword(request.getPassword());
        context.setCaptcha(request.getCaptcha());
        context.setDeviceId(request.getDeviceId());
        context.setDeviceType(request.getDeviceType());
        context.setIp(ip);

        // 组装责任链: 密码校验 → 封禁检测 → 多设备管理
        LoginHandler chain = passwordCheckHandler;
        chain.setNext(banCheckHandler)
             .setNext(multiDeviceHandler);

        if (!chain.handle(context)) {
            throw new BusinessException(context.getResultCode());
        }

        User user = context.getUser();
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return buildLoginResponse(user, request.getDeviceId(), request.getDeviceType(), ip);
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        try {
            Long userId = jwtUtils.getUserId(refreshToken);
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(ResultCode.TOKEN_INVALID);
            }
            // 封禁用户不允许刷新 token
            if (user.getStatus() != null && user.getStatus() == User.STATUS_BANNED) {
                throw new BusinessException(ResultCode.USER_BANNED);
            }
            // 校验设备记录仍有效（未被踢出）
            String deviceId = jwtUtils.getDeviceId(refreshToken);
            if (deviceId != null) {
                LoginDevice device = loginDeviceMapper.selectByUserIdAndDeviceId(userId, deviceId);
                if (device == null) {
                    throw new BusinessException(ResultCode.TOKEN_INVALID, "设备已被踢出");
                }
            }
            String newAccessToken = jwtUtils.generateAccessToken(userId);
            String newRefreshToken = jwtUtils.generateRefreshToken(userId, deviceId);
            return new LoginResponse(newAccessToken, newRefreshToken, 7200, userId, user.getNickname());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
    }

    @Override
    public void logout(Long userId, String deviceId) {
        // 逻辑删除设备记录，该设备的 refreshToken 立即失效
        loginDeviceMapper.deleteByUserIdAndDeviceId(userId, deviceId);
    }

    /** 生成双 token + 记录登录设备 */
    private LoginResponse buildLoginResponse(User user, String deviceId, String deviceType, String ip) {
        String accessToken = jwtUtils.generateAccessToken(user.getId());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), deviceId);

        LoginDevice device = new LoginDevice();
        device.setId(idGenerator.nextId());
        device.setUserId(user.getId());
        device.setDeviceId(deviceId);
        device.setDeviceType(deviceType);
        device.setIp(ip);
        device.setRefreshToken(refreshToken);
        device.setLastActiveAt(LocalDateTime.now());
        device.setDeleted(0);
        device.setCreatedAt(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());
        loginDeviceMapper.insert(device);

        return new LoginResponse(accessToken, refreshToken, 7200, user.getId(), user.getNickname());
    }

}
