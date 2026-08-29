package com.ltcpond.msgrelay.reliability.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息已读 Bitmap — 用于群聊消息的投递/已读状态管理
 *
 * 512 字节 Bitmap 支持 4096 人群聊
 */
@Data
public class MsgReadBitmap {

    /** 消息 ID */
    private Long msgId;
    /** 群 ID */
    private Long groupId;
    /** 投递 Bitmap（512 字节 = 4096 位） */
    private byte[] deliveredBitmap;
    /** 已投递人数 */
    private Integer deliveredCount;
    /** 已读 Bitmap（512 字节 = 4096 位） */
    private byte[] readBitmap;
    /** 已读人数 */
    private Integer readCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
