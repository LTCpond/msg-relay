package com.ltcpond.msgrelay.user.service;

import com.ltcpond.msgrelay.user.model.dto.LoginRequest;
import com.ltcpond.msgrelay.user.model.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request, String ip);

    LoginResponse refreshToken(String refreshToken);

    void logout(Long userId, String deviceId);
}
