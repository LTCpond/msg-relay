package com.ltcpond.msgrelay.user.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Team extends BaseEntity {

    /** 团队名称 */
    private String name;

    /** 团队Logo URL */
    private String logo;

    /** 所有者用户ID */
    private Long ownerId;

    /** 最大成员数 */
    private Integer maxMembers;
}
