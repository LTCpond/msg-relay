package com.ltcpond.msgrelay.common.auth;

/** 由用户模块提供实现，避免 common 反向依赖 user。 */
public interface LoginSessionValidator {
    boolean isValid(Long userId, String deviceId);
}
