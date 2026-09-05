package com.ltcpond.msgrelay.common.lock;

/** 仅表示分布式锁未能取得，和锁内业务异常严格区分。 */
public class LockAcquisitionException extends RuntimeException {
    public LockAcquisitionException(String message) { super(message); }
    public LockAcquisitionException(String message, Throwable cause) { super(message, cause); }
}
