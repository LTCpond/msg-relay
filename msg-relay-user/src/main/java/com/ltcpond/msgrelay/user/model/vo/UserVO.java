package com.ltcpond.msgrelay.user.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    /** 用户ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 工号 */
    private String jobNumber;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 所属团队ID */
    private Long teamId;

    /** 所属部门ID */
    private Long deptId;

    /** 状态: 1=正常, 2=封禁 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
