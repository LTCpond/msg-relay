package com.ltcpond.msgrelay.group.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupMember extends BaseEntity {

    private Long groupId;
    private Long userId;
    private Integer role;
    private Boolean isMuted;
    private LocalDateTime muteExpireAt;
}
