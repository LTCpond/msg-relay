package com.ltcpond.msgrelay.user.service;

import com.ltcpond.msgrelay.user.model.dto.CreateDeptRequest;
import com.ltcpond.msgrelay.user.model.vo.DepartmentVO;
import com.ltcpond.msgrelay.user.model.vo.UserVO;

import java.util.List;

public interface DepartmentService {

    DepartmentVO create(CreateDeptRequest request);

    List<DepartmentVO> listByTeamId(Long teamId);

    DepartmentVO getByUserId(Long userId);

    void delete(Long deptId, Long userId);

    /** 更换部门 */
    void changeDept(Long memberId, Long newDeptId, Long operatorId);

    List<UserVO> listMembers(Long deptId);
}
