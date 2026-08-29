package com.ltcpond.msgrelay.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "canal")
public class CanalProperties {

    /** Canal server host */
    private String host = "127.0.0.1";

    /** Canal server port */
    private int port = 11111;

    /** Canal destination */
    private String destination = "example";

    /** 订阅的表过滤表达式 */
    private String subscribe = "msg-relay\\.(t_user|t_department|t_message|t_conversation|t_group_member)";
}
