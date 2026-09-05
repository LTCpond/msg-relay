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

    int upsertSnapshot(@Param("msgId") Long msgId, @Param("groupId") Long groupId,
                       @Param("deliveredBitmap") byte[] deliveredBitmap,
                       @Param("deliveredCount") int deliveredCount,
                       @Param("readBitmap") byte[] readBitmap,
                       @Param("readCount") int readCount);
}
