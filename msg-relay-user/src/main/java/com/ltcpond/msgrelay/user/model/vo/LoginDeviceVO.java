package com.ltcpond.msgrelay.user.model.vo;

import com.ltcpond.msgrelay.user.model.entity.LoginDevice;
import java.time.LocalDateTime;

/** 设备列表只暴露客户端需要的登录信息。 */
public record LoginDeviceVO(String deviceId, String deviceType, String ip, LocalDateTime lastActiveAt) {
    public static LoginDeviceVO from(LoginDevice device) {
        return new LoginDeviceVO(device.getDeviceId(), device.getDeviceType(),
                device.getIp(), device.getLastActiveAt());
    }
}
