package com.ltcpond.msgrelay.reliability.repository;

import com.ltcpond.msgrelay.reliability.model.entity.MsgReadBitmap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MsgReadBitmapMapper {

    int insert(MsgReadBitmap record);

    int updateDeliveredBitmap(@Param("msgId") Long msgId,
                              @Param("bitmap") byte[] bitmap);

    int updateReadBitmap(@Param("msgId") Long msgId,
                         @Param("bitmap") byte[] bitmap);

    int incrementDeliveredCount(@Param("msgId") Long msgId);

    int incrementReadCount(@Param("msgId") Long msgId);

    MsgReadBitmap selectByMsgId(@Param("msgId") Long msgId);
}
