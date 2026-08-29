package com.ltcpond.msgrelay.common.base;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类 — 所有数据库实体继承此类，统一提供：
 * id: 业务生成（雪花算法），不再依赖数据库自增
 * createdAt/updatedAt: 应用层手动设置（替代 MP 自动填充）
 * deleted: 逻辑删除标记（0=正常, 1=已删除）
 */
@Data
public abstract class BaseEntity implements Serializable {

    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
