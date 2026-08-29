package com.ltcpond.msgrelay.reliability.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 消息状态 Mapper — 跨模块更新 t_message 表 */
@Mapper
public interface MessageReadMapper {

    /** 更新消息状态 */
    void updateStatus(@Param("msgId") Long msgId, @Param("status") Integer status);
}
