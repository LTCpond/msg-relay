package com.ltcpond.msgrelay.user.service;

import com.ltcpond.msgrelay.user.model.dto.UserUpdateDTO;
import com.ltcpond.msgrelay.user.model.vo.UserVO;

import java.util.List;

public interface UserService {

    UserVO getById(Long userId);

    UserVO getByUsername(String username);

    void update(Long userId, UserUpdateDTO dto);

    List<UserVO> searchByKeyword(Long teamId, String keyword);

    List<UserVO> listByTeamId(Long teamId);
}
