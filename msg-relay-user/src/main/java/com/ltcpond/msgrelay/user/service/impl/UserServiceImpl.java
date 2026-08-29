package com.ltcpond.msgrelay.user.service.impl;
import com.ltcpond.msgrelay.user.service.UserService;

import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import com.ltcpond.msgrelay.user.model.dto.UserUpdateDTO;
import com.ltcpond.msgrelay.user.model.entity.User;
import com.ltcpond.msgrelay.user.model.vo.UserVO;
import com.ltcpond.msgrelay.user.repository.UserMapper;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务 — 多级缓存读 + 缓存失效策略
 *
 * 查询链路: Caffeine(L1,5min) → Redis(L2,30min) → MySQL(L3)
 * 更新策略: 先写 DB → 再删缓存（Cache Aside 模式）
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private MultiLevelCache multiLevelCache;

    @Override
    public UserVO getById(Long userId) {
        String cacheKey = "user:id:" + userId;
        // 三级缓存查询：Caffeine → Redis → MySQL（加分布式锁防击穿）
        User user = multiLevelCache.get(cacheKey, User.class,
                key -> userMapper.selectById(userId), 1800);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    @Override
    public UserVO getByUsername(String username) {
        // 用户名查用户不走缓存（管理后台低频操作）
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    @Override
    public void update(Long userId, UserUpdateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public List<UserVO> searchByKeyword(Long teamId, String keyword) {
        return userMapper.searchByTeamIdAndKeyword(teamId, keyword).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserVO> listByTeamId(Long teamId) {
        return userMapper.selectByTeamId(teamId).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setJobNumber(user.getJobNumber());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setTeamId(user.getTeamId());
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
