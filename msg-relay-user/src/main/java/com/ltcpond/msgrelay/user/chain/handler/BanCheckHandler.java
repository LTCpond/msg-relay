package com.ltcpond.msgrelay.user.chain.handler;

import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.user.chain.LoginContext;
import com.ltcpond.msgrelay.user.chain.LoginHandler;
import com.ltcpond.msgrelay.user.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 封禁检测 — 责任链第二节点
 * 检查用户状态: 1=正常通过, 2=封禁拒绝
 */
@Slf4j
@Component
public class BanCheckHandler extends LoginHandler {

    @Override
    public boolean handle(LoginContext context) {
        if (context.getUser() != null && context.getUser().getStatus() != null
                && context.getUser().getStatus() == User.STATUS_BANNED) {
            context.setResultCode(ResultCode.USER_BANNED);
            return false;
        }
        return next(context);
    }
}
