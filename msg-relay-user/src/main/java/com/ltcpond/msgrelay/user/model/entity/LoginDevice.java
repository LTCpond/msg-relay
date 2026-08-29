package com.ltcpond.msgrelay.user.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class LoginDevice extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 设备唯一标识 */
    private String deviceId;

    /** 设备类型: PC/ANDROID/IOS/WEB */
    private String deviceType;

    /** 登录IP */
    private String ip;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

    /** 刷新Token */
    private String refreshToken;
}
