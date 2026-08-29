package com.ltcpond.msgrelay.common.ratelimit;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** QPS限制 */
    int qps() default 100;

    /** 限流后的提示信息 */
    String message() default "请求过于频繁，请稍后再试";

    /** 限流粒度 */
    KeyType keyType() default KeyType.METHOD;

    enum KeyType {
        METHOD,  // 方法级别（默认）
        USER,    // 用户级别
        DEVICE,  // 设备级别
        IP       // IP 级别
    }
}
