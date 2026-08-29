package com.ltcpond.msgrelay.user.model.vo;

import lombok.Data;

@Data
public class DepartmentVO {

    /** 部门ID */
    private Long id;

    /** 部门名称 */
    private String name;

    /** 排序号 */
    private Integer sortOrder;
}
