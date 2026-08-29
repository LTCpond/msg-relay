package com.ltcpond.msgrelay.user.chain;

import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.user.model.entity.User;
import lombok.Data;

/**
 * 登录上下文 — 在责任链各节点间传递登录信息
 * 包含请求参数和中间处理结果
 */
@Data
public class LoginContext {

    /** 请求参数 */
    private String username;
    private String password;
    private String captcha;
    private String deviceId;
    private String deviceType;
    private String ip;

    /** 处理结果 */
    private User user;
    private ResultCode resultCode;
}
