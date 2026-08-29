package com.ltcpond.msgrelay.user.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Department extends BaseEntity {

    /** 所属团队ID */
    private Long teamId;

    /** 部门名称 */
    private String name;

    /** 排序号 */
    private Integer sortOrder;
}
