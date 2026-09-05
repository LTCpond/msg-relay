package com.ltcpond.msgrelay.im.model.entity;

import com.ltcpond.msgrelay.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** IM 消息实体 — 单聊和群聊消息通用 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Message extends BaseEntity {

    /** 全局唯一消息 ID（雪花算法） */
    private Long msgId;
    /** 客户端幂等消息 ID，与 senderId 组成唯一键 */
    private String clientMsgId;
    /** 发送者 UID */
    private Long senderId;
    /** 接收者 ID（用户 ID 或群 ID，由 receiverType 区分） */
    private Long receiverId;
    /** 接收者类型: 1=单聊 2=群聊 */
    private Integer receiverType;
    /** 消息类型: 1=文本 2=图片 3=文件 4=语音 5=系统通知 */
    private Integer msgType;
    /** 消息正文 */
    private String content;
    /** 扩展 JSON（引用消息、@提醒等） */
    private String extraJson;
    /** 媒体元数据 JSON（图片宽高、语音时长、文件名/大小） */
    private String mediaMetaJson;
    /** 投递状态: 0=发送中 1=已发送 2=已投递 3=已读 4=已撤回；群聊由 receiverType 区分 */
    private Integer status;
}
