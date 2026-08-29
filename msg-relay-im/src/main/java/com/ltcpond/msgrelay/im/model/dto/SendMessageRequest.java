package com.ltcpond.msgrelay.im.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 发送消息请求 DTO */
@Data
public class SendMessageRequest {

    /** 接收者 ID（用户或群） */
    @NotNull(message = "接收者不能为空")
    private Long receiverId;

    /** 接收者类型: 1=单聊 2=群聊 */
    @NotNull(message = "接收者类型不能为空")
    private Integer receiverType;

    /** 消息类型: 1=文本 2=图片 3=文件 4=语音 5=系统通知 */
    @NotNull(message = "消息类型不能为空")
    private Integer msgType;

    /** 消息正文 */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 扩展 JSON（引用消息、@提醒等） */
    private String extraJson;

    /** 媒体元数据 JSON（图片宽高、语音时长、文件名/大小） */
    private String mediaMetaJson;
}
