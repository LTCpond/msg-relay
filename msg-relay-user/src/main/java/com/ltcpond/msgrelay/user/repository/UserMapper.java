package com.ltcpond.msgrelay.user.repository;

import com.ltcpond.msgrelay.user.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 用户 Mapper */
@Mapper
public interface UserMapper {

    int insert(User user);

    int updateById(User user);

    User selectById(@Param("id") Long id);

    /** 登录事务锁定用户行，串行执行该用户的设备名额检查和会话创建。 */
    User selectByIdForUpdate(@Param("id") Long id);

    /** 根据 username 全局查询 */
    User selectByUsername(@Param("username") String username);

    List<User> selectAll();

    Long countByUsername(@Param("username") String username);

    /** 工号企业内唯一性校验 */
    Long countByJobNumberAndTeamId(@Param("jobNumber") String jobNumber, @Param("teamId") Long teamId);

    /** 审批通过后更新用户的企业信息 */
    int updateApprovalFields(@Param("id") Long id, @Param("teamId") Long teamId,
                             @Param("jobNumber") String jobNumber, @Param("nickname") String nickname,
                             @Param("deptId") Long deptId);

    int deleteByIdLogic(@Param("id") Long id);

    List<User> selectByTeamId(@Param("teamId") Long teamId);

    List<User> selectByDeptId(@Param("deptId") Long deptId);

    /** 按工号或昵称搜索企业内成员 */
    List<User> searchByTeamIdAndKeyword(@Param("teamId") Long teamId, @Param("keyword") String keyword);
}
