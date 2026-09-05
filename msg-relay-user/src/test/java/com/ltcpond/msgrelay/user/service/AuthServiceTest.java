package com.ltcpond.msgrelay.user.service;

import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.common.utils.JwtUtils;
import com.ltcpond.msgrelay.common.utils.SnowflakeIdGenerator;
import com.ltcpond.msgrelay.user.chain.handler.*;
import com.ltcpond.msgrelay.user.model.dto.LoginRequest;
import com.ltcpond.msgrelay.user.model.entity.LoginDevice;
import com.ltcpond.msgrelay.user.model.entity.User;
import com.ltcpond.msgrelay.user.repository.LoginDeviceMapper;
import com.ltcpond.msgrelay.user.repository.UserMapper;
import com.ltcpond.msgrelay.user.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private AuthServiceImpl auth;
    private UserMapper users;
    private LoginDeviceMapper devices;
    private LoginSessionService sessions;
    private JwtUtils jwt;
    private User user;

    @BeforeEach
    void setup() {
        users = mock(UserMapper.class);
        devices = mock(LoginDeviceMapper.class);
        sessions = mock(LoginSessionService.class);
        jwt = spy(new JwtUtils());
        ReflectionTestUtils.setField(jwt, "secret", "test-secret-with-at-least-32-bytes-for-hmac");
        ReflectionTestUtils.setField(jwt, "accessTokenExpire", 7200L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpire", 604800L);
        user = new User();
        user.setId(1L);
        user.setNickname("Alice");
        user.setStatus(1);
        user.setPassword(new BCryptPasswordEncoder(4).encode("password"));
        when(users.selectByUsername("alice")).thenReturn(user);
        when(users.selectById(1L)).thenReturn(user);
        when(users.selectByIdForUpdate(1L)).thenReturn(user);
        var passwords = new PasswordCheckHandler();
        ReflectionTestUtils.setField(passwords, "userMapper", users);
        var multi = new MultiDeviceHandler();
        ReflectionTestUtils.setField(multi, "userMapper", users);
        ReflectionTestUtils.setField(multi, "loginDeviceMapper", devices);
        ReflectionTestUtils.setField(multi, "loginSessionService", sessions);
        auth = new AuthServiceImpl();
        ReflectionTestUtils.setField(auth, "userMapper", users);
        ReflectionTestUtils.setField(auth, "loginDeviceMapper", devices);
        ReflectionTestUtils.setField(auth, "loginSessionService", sessions);
        ReflectionTestUtils.setField(auth, "jwtUtils", jwt);
        ReflectionTestUtils.setField(auth, "idGenerator", mock(SnowflakeIdGenerator.class));
        ReflectionTestUtils.setField(auth, "passwordCheckHandler", passwords);
        ReflectionTestUtils.setField(auth, "banCheckHandler", new BanCheckHandler());
        ReflectionTestUtils.setField(auth, "multiDeviceHandler", multi);
    }

    private LoginRequest request() {
        var request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password");
        request.setDeviceId("PC");
        request.setDeviceType("WEB");
        return request;
    }

    @Test
    void createsSessionBeforeIssuingTokens() {
        var response = auth.login(request(), "127.0.0.1");
        var order = inOrder(devices, jwt);
        order.verify(devices).insert(any());
        verify(sessions).sessionChanged(1L, "PC");
        order.verify(jwt).generateAccessToken(1L, "PC");
        order.verify(jwt).generateRefreshToken(1L, "PC");
        assertTrue(jwt.validateAccessToken(response.getAccessToken()));
        assertTrue(jwt.validateRefreshToken(response.getRefreshToken()));
        assertEquals(7200L, response.getExpiresIn());
    }

    @Test
    void repeatedLoginReusesExistingDeviceWithoutEviction() {
        var existing = new LoginDevice();
        existing.setId(10L);
        existing.setDeviceId("PC");
        existing.setDeleted(0);
        when(devices.existsByUserIdAndDeviceId(1L, "PC")).thenReturn(true);
        when(devices.selectByUserIdAndDeviceIdIncludingDeleted(1L, "PC")).thenReturn(existing);
        for (int i = 0; i < 20; i++) {
            auth.login(request(), "127.0.0.2");
        }
        verify(devices, times(20)).updateById(existing);
        verify(devices, never()).insert(any());
        verify(devices, never()).countByUserId(anyLong());
        verify(sessions, times(20)).sessionChanged(1L, "PC");
        verify(sessions, never()).revoke(anyLong(), anyString());
        assertEquals("127.0.0.2", existing.getIp());
        assertEquals("WEB", existing.getDeviceType());
    }

    @Test
    void restoresDeletedDeviceAndEvictsOldestWhenFiveOtherDevicesExist() {
        var existing = new LoginDevice();
        existing.setId(10L);
        existing.setDeleted(1);
        when(devices.selectByUserIdAndDeviceIdIncludingDeleted(1L, "PC")).thenReturn(existing);
        when(devices.countByUserId(1L)).thenReturn(5L);
        List<LoginDevice> active = IntStream.range(0, 5).mapToObj(i -> {
            var device = new LoginDevice();
            device.setDeviceId("device-" + i);
            return device;
        }).toList();
        when(devices.selectByUserId(1L)).thenReturn(active);
        auth.login(request(), "127.0.0.1");
        verify(sessions).revoke(1L, "device-4");
        verify(devices).updateById(existing);
        verify(devices, never()).insert(any());
        assertEquals(0, existing.getDeleted());
    }

    @Test
    void refreshRequiresRefreshTypeAndAnActiveDeviceSession() {
        String refresh = jwt.generateRefreshToken(1L, "PC");
        assertThrows(BusinessException.class, () -> auth.refreshToken(jwt.generateAccessToken(1L, "PC")));
        assertThrows(BusinessException.class, () -> auth.refreshToken(refresh));
        when(sessions.isValid(1L, "PC")).thenReturn(true);
        assertTrue(jwt.validateAccessToken(auth.refreshToken(refresh).getAccessToken()));
        // 没有 rotation，旧 RT 在会话有效时仍可重复续期。
        assertTrue(jwt.validateRefreshToken(auth.refreshToken(refresh).getRefreshToken()));
        when(sessions.isValid(1L, "PC")).thenReturn(false);
        assertThrows(BusinessException.class, () -> auth.refreshToken(refresh));
        verify(devices, never()).insert(any());
        verify(devices, never()).updateById(any());
    }

    @Test
    void bannedOrMissingUserCannotRefresh() {
        when(sessions.isValid(1L, "PC")).thenReturn(true);
        String refresh = jwt.generateRefreshToken(1L, "PC");
        user.setStatus(User.STATUS_BANNED);
        var error = assertThrows(BusinessException.class, () -> auth.refreshToken(refresh));
        assertEquals(ResultCode.USER_BANNED.getCode(), error.getCode());
        when(users.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> auth.refreshToken(refresh));
    }
}
