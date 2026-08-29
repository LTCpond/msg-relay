package com.ltcpond.msgrelay.user.controller;

import com.ltcpond.msgrelay.common.ratelimit.RateLimit;
import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.user.model.dto.LoginRequest;
import com.ltcpond.msgrelay.user.model.dto.LoginResponse;
import com.ltcpond.msgrelay.user.service.AuthService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @PostMapping("/login")
    @RateLimit(qps = 10, keyType = RateLimit.KeyType.DEVICE, message = "该设备登录过于频繁")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                        HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        LoginResponse response = authService.login(request, ip);
        return Result.ok(response);
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestParam String refreshToken) {
        return Result.ok(authService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String deviceId = request.getParameter("deviceId");
        authService.logout(userId, deviceId);
        return Result.ok();
    }
}
