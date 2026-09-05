package com.ltcpond.msgrelay.user.service.impl;
import com.ltcpond.msgrelay.user.service.AuthService;
import com.ltcpond.msgrelay.user.service.LoginSessionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

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
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 认证服务 — 用户登录/登出/刷新Token
 *
 * 登录流程:
 * 1. 构建 LoginContext，传入责任链
 * 2. PasswordCheck → BanCheck → MultiDevice 依次校验
 * 3. 先创建/恢复设备登录会话，再签发双 token
 *
 * 双 Token 设计:
 * - accessToken (2h): 随 API 请求携带，短期有效减少泄露风险
 * - refreshToken (7d): 无状态 JWT，只负责续期；设备会话决定是否允许续期
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

    @Resource
    private LoginSessionService loginSessionService;

    @Resource
    private PasswordCheckHandler passwordCheckHandler;

    @Resource
    private BanCheckHandler banCheckHandler;

    @Resource
    private MultiDeviceHandler multiDeviceHandler;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        Claims claims;
        try {
            claims = jwtUtils.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        Long userId = Long.valueOf(claims.getSubject());
        String deviceId = claims.get("deviceId", String.class);
        if (!loginSessionService.isValid(userId, deviceId)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "登录状态已失效");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        if (user.getStatus() != null && user.getStatus() == User.STATUS_BANNED) {
            throw new BusinessException(ResultCode.USER_BANNED);
        }
        return issueTokens(user, deviceId);
    }

    @Override
    public void logout(Long userId, String deviceId) {
        loginSessionService.revoke(userId, deviceId);
    }

    /** 先创建/恢复设备登录会话，再签发凭证。 */
    private LoginResponse buildLoginResponse(User user, String deviceId, String deviceType, String ip) {
        saveOrRefreshLoginDevice(user.getId(), deviceId, deviceType, ip);
        loginSessionService.sessionChanged(user.getId(), deviceId);
        return issueTokens(user, deviceId);
    }

    private LoginResponse issueTokens(User user, String deviceId) {
        return new LoginResponse(jwtUtils.generateAccessToken(user.getId(), deviceId),
                jwtUtils.generateRefreshToken(user.getId(), deviceId),
                jwtUtils.getAccessTokenExpire(), user.getId(), user.getNickname());
    }

    private void saveOrRefreshLoginDevice(Long userId, String deviceId, String deviceType, String ip) {
        LoginDevice device = loginDeviceMapper.selectByUserIdAndDeviceIdIncludingDeleted(userId, deviceId);
        boolean isNew = device == null;
        if (isNew) {
            device = new LoginDevice();
            device.setId(idGenerator.nextId());
            device.setUserId(userId);
            device.setDeviceId(deviceId);
            device.setCreatedAt(LocalDateTime.now());
        }
        device.setDeviceType(deviceType);
        device.setIp(ip);
        device.setLastActiveAt(LocalDateTime.now());
        device.setDeleted(0);
        device.setUpdatedAt(LocalDateTime.now());
        if (isNew) {
            loginDeviceMapper.insert(device);
        } else {
            loginDeviceMapper.updateById(device);
        }
    }
}
