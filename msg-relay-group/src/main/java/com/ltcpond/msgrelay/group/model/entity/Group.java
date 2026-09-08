package com.ltcpond.msgrelay.group.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Group extends BaseEntity {

    private Long conversationId;
    private Long teamId;
    private String name;
    private String avatar;
    private Long ownerId;
    private String announcement;
    private Integer maxMembers;
    private Boolean isMutedAll;
}
