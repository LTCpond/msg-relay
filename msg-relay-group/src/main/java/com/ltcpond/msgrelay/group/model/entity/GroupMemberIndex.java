package com.ltcpond.msgrelay.group.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 群成员位序号映射 — 用于 Bitmap 已读状态管理
 *
 * 位序号只增不复用，避免历史消息已读状态错乱
 * 成员退出后标记为"已退出"，保留位序号映射
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GroupMemberIndex extends BaseEntity {

    /** 群 ID */
    private Long groupId;
    /** 用户 ID */
    private Long userId;
    /** 位序号（0~N，只增不复用） */
    private Integer memberIndex;
    /** 状态: 1=活跃 2=已退出 */
    private Integer status;
    /** 加入时间 */
    private LocalDateTime joinedAt;
    /** 退出时间 */
    private LocalDateTime leftAt;
}
