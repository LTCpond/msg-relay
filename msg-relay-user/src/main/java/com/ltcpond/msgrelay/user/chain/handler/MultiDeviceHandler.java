package com.ltcpond.msgrelay.user.chain.handler;

import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.user.chain.LoginContext;
import com.ltcpond.msgrelay.user.chain.LoginHandler;
import com.ltcpond.msgrelay.user.model.entity.LoginDevice;
import com.ltcpond.msgrelay.user.model.entity.User;
import com.ltcpond.msgrelay.user.repository.LoginDeviceMapper;
import com.ltcpond.msgrelay.user.repository.UserMapper;
import com.ltcpond.msgrelay.user.service.LoginSessionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/** 最多五个有效设备登录会话，重复登录同一设备不增加名额。 */
@Component
public class MultiDeviceHandler extends LoginHandler {
    @Resource
    private LoginDeviceMapper loginDeviceMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private LoginSessionService loginSessionService;

    @Override
    public boolean handle(LoginContext context) {
        Long userId = context.getUser().getId();
        // login 的事务持有用户行锁，覆盖名额检查和后续会话写入，避免并发超额。
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            context.setResultCode(ResultCode.TOKEN_INVALID);
            return false;
        }
        if (Integer.valueOf(User.STATUS_BANNED).equals(user.getStatus())) {
            context.setResultCode(ResultCode.USER_BANNED);
            return false;
        }
        context.setUser(user);
        if (loginDeviceMapper.existsByUserIdAndDeviceId(userId, context.getDeviceId())) {
            return next(context);
        }
        long count = loginDeviceMapper.countByUserId(userId);
        if (count >= 5) {
            List<LoginDevice> devices = loginDeviceMapper.selectByUserId(userId);
            // 列表按最后活跃时间降序排列；也兼容历史数据已经超过五台的情况。
            for (int i = devices.size() - 1; i >= 0 && count >= 5; i--, count--) {
                loginSessionService.revoke(userId, devices.get(i).getDeviceId());
            }
        }
        return next(context);
    }
}
