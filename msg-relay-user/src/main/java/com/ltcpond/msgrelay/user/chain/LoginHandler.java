package com.ltcpond.msgrelay.user.chain;

/**
 * 责任链抽象处理器 — 登录验证流程的顺序控制
 *
 * 链式调用: PasswordCheck → BanCheck → MultiDevice
 * 任一 handler 返回 false 则链路中断，登录失败
 */
public abstract class LoginHandler {

    protected LoginHandler next;

    public LoginHandler setNext(LoginHandler next) {
        this.next = next;
        return next;
    }

    /** 返回 true 继续执行下一个 handler，false 中断 */
    public abstract boolean handle(LoginContext context);

    protected boolean next(LoginContext context) {
        if (next == null) {
            return true;
        }
        return next.handle(context);
    }
}
