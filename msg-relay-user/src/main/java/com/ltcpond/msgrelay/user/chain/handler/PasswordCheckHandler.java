package com.ltcpond.msgrelay.user.chain.handler;

import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.user.chain.LoginContext;
import com.ltcpond.msgrelay.user.chain.LoginHandler;
import com.ltcpond.msgrelay.user.model.entity.User;
import com.ltcpond.msgrelay.user.repository.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 密码校验 — 责任链第一节点
 *
 * 流程: 根据用户名查 DB → BCrypt 比对密码哈希 → 通过则将 User 放入上下文
 */
@Slf4j
@Component
public class PasswordCheckHandler extends LoginHandler {

    @Resource
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public boolean handle(LoginContext context) {
        User user = userMapper.selectByUsername(context.getUsername());
        if (user == null) {
            context.setResultCode(ResultCode.USER_NOT_FOUND);
            return false;
        }
        if (!passwordEncoder.matches(context.getPassword(), user.getPassword())) {
            context.setResultCode(ResultCode.PASSWORD_ERROR);
            return false;
        }
        context.setUser(user);
        return next(context);
    }
}
