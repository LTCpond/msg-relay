package com.ltcpond.msgrelay.im.netty;

/**
 * 认证成功事件
 *
 * 由 WebSocketHandler 在认证成功后通过 ctx.fireUserEventTriggered 触发，
 * 通知 HeartbeatHandler 启动心跳状态机。
 */
public class AuthenticationSuccessEvent {
}