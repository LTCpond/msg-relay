package com.ltcpond.msgrelay.im.repository;

import com.ltcpond.msgrelay.im.model.entity.UserMessageHide;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 用户消息隐藏 Mapper — 管理用户隐藏的消息记录 */
@Mapper
public interface UserMessageHideMapper {

    int insert(UserMessageHide hide);

    /** 判断用户是否已隐藏某条消息 */
    boolean exists(@Param("userId") Long userId, @Param("msgId") Long msgId);

    /** 查询用户已隐藏的所有消息 ID */
    List<Long> selectMsgIdsByUserId(@Param("userId") Long userId);
}
