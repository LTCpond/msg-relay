package com.ltcpond.msgrelay.im.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 消息已读状态视图 — 群聊已读/未读成员列表 */
@Data
public class ReadStatusVO {

    private Long msgId;
    /** 已读人数 */
    private Integer readCount;
    /** 已读用户列表 */
    private List<ReadUser> readList;
    /** 未读用户列表（仅群聊） */
    private List<ReadUser> unreadList;

    /** 已读/未读用户信息 */
    @Data
    public static class ReadUser {
        private Long userId;
        private String userName;
        /** 阅读时间，未读时为 null */
        private LocalDateTime readAt;

        public ReadUser(Long userId, String userName, LocalDateTime readAt) {
            this.userId = userId;
            this.userName = userName;
            this.readAt = readAt;
        }
    }
}
