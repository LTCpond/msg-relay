package com.ltcpond.msgrelay.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // Business errors
    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_ERROR(1002, "密码错误"),
    USER_BANNED(1003, "用户已被封禁"),
    TOKEN_EXPIRED(1004, "Token已过期"),
    TOKEN_INVALID(1005, "Token无效"),
    DEVICE_KICKED(1006, "设备已被踢下线"),
    NOT_TEAM_OWNER(1008, "非团队所有者，无权操作"),
    MSG_SEND_FAILED(2001, "消息发送失败"),
    MSG_RECALL_TIMEOUT(2002, "消息撤回超时"),
    GROUP_NOT_FOUND(3001, "群组不存在"),
    NOT_GROUP_MEMBER(3002, "非群成员"),
    FILE_UPLOAD_FAILED(4001, "文件上传失败"),
    FILE_TOO_LARGE(4002, "文件大小超限");

    private final int code;
    private final String message;
}
