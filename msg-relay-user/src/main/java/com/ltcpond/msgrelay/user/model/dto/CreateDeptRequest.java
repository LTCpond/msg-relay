package com.ltcpond.msgrelay.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateDeptRequest {

    /** 所属团队ID */
    @NotNull(message = "团队ID不能为空")
    private Long teamId;

    /** 部门名称 */
    @NotBlank(message = "部门名称不能为空")
    private String name;

    /** 排序号 */
    private Integer sortOrder;
}
