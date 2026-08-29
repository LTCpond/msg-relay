package com.ltcpond.msgrelay.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    /** 用户名(全局唯一) */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码 */
    private String captcha;

    /** 设备唯一标识 */
    private String deviceId;

    /** 设备类型: PC/ANDROID/IOS/WEB */
    private String deviceType;
}
