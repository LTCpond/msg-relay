package com.ltcpond.msgrelay.user.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    /** 访问令牌, 有效期2小时 */
    private String accessToken;

    /** 刷新令牌, 有效期7天 */
    private String refreshToken;

    /** 过期时间(秒) */
    private long expiresIn;

    /** 用户ID */
    private Long userId;

    /** 昵称 */
    private String nickname;
}
