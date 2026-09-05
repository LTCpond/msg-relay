package com.ltcpond.msgrelay.im.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 消息视图对象 — 返回给前端的消息展示模型 */
@Data
public class MessageVO {

    private Long id;
    /** 全局唯一消息 ID */
    private Long msgId;
    /** 客户端幂等消息 ID */
    private String clientMsgId;
    /** 发送者 UID */
    private Long senderId;
    /** 发送者名称 */
    private String senderName;
    /** 接收者 ID */
    private Long receiverId;
    /** 接收者类型: 1=单聊 2=群聊 */
    private Integer receiverType;
    /** 消息类型 */
    private Integer msgType;
    /** 消息正文 */
    private String content;
    /** 扩展 JSON */
    private String extraJson;
    /** 媒体元数据 JSON */
    private String mediaMetaJson;
    /** 投递状态: 0=发送中 1=已发送 2=已投递 3=已读 4=已撤回 */
    private Integer status;
    private LocalDateTime createdAt;
}
