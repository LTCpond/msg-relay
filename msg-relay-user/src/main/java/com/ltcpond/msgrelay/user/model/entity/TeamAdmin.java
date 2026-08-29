package com.ltcpond.msgrelay.user.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 团队管理员 */
@Data
public class TeamAdmin {

    private Long id;
    private Long teamId;
    private Long userId;
    private LocalDateTime createdAt;
    private Integer deleted;
}
