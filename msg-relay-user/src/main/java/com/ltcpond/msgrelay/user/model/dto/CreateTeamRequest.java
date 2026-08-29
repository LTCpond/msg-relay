package com.ltcpond.msgrelay.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTeamRequest {

    /** 团队名称 */
    @NotBlank(message = "团队名称不能为空")
    private String name;

    /** 团队Logo URL */
    private String logo;

    /** 最大成员数 */
    private Integer maxMembers;
}
