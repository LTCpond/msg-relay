package com.ltcpond.msgrelay.user.model.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;
}
