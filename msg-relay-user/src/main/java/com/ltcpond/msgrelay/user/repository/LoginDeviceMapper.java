package com.ltcpond.msgrelay.user.repository;

import com.ltcpond.msgrelay.user.model.entity.LoginDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LoginDeviceMapper {

    int insert(LoginDevice device);

    int updateById(LoginDevice device);

    LoginDevice selectById(@Param("id") Long id);

    List<LoginDevice> selectByUserId(@Param("userId") Long userId);

    Long countByUserId(@Param("userId") Long userId);

    int deleteByIdLogic(@Param("id") Long id);

    int deleteByUserIdAndDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    LoginDevice selectByUserIdAndDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    LoginDevice selectByUserIdAndDeviceIdIncludingDeleted(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    boolean existsByUserIdAndDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);
}
