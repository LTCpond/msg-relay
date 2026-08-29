package com.ltcpond.msgrelay.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理员创建成员请求 */
@Data
public class CreateMemberRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "工号不能为空")
    private String jobNumber;

    @NotBlank(message = "昵称不能为空，建议使用真实姓名")
    private String nickname;

    @NotNull(message = "部门ID不能为空")
    private Long deptId;

    private String email;

    private String phone;
}
