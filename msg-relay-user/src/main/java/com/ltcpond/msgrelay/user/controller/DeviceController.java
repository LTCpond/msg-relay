package com.ltcpond.msgrelay.user.controller;

import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.user.model.vo.LoginDeviceVO;
import com.ltcpond.msgrelay.user.repository.LoginDeviceMapper;
import com.ltcpond.msgrelay.user.service.LoginSessionService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 设备登录会话列表及远端撤销。 */
@RestController
@RequestMapping("/api/user/device")
public class DeviceController {
    @Resource
    private LoginDeviceMapper loginDeviceMapper;
    @Resource
    private LoginSessionService loginSessionService;

    @GetMapping("/list")
    public Result<List<LoginDeviceVO>> listDevices(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(loginDeviceMapper.selectByUserId(userId).stream()
                .map(LoginDeviceVO::from).toList());
    }

    @DeleteMapping("/{deviceId}")
    public Result<Void> kickDevice(@PathVariable String deviceId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        loginSessionService.revoke(userId, deviceId);
        return Result.ok();
    }
}
