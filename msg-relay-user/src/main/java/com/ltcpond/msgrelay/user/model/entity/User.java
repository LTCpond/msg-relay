package com.ltcpond.msgrelay.user.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_BANNED = 2;

    /** 用户名(全局唯一) */
    private String username;

    /** BCrypt密码 */
    private String password;

    /** 工号(企业内唯一，加入团队时设置) */
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
}
