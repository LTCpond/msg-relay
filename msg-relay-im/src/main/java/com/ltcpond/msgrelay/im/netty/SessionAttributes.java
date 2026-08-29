package com.ltcpond.msgrelay.im.netty;

import io.netty.util.AttributeKey;

/** Netty Channel 属性常量 — 将 userId 和 deviceId 绑定到 Channel 上 */
public final class SessionAttributes {

    /** Channel 上的 userId 属性键 */
    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("userId");

    /** Channel 上的 deviceId 属性键 */
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("deviceId");

    private SessionAttributes() {}
}
