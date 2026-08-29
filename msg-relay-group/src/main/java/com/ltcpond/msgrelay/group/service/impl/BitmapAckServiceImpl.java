package com.ltcpond.msgrelay.group.service.impl;

import com.ltcpond.msgrelay.group.model.entity.GroupMemberIndex;
import com.ltcpond.msgrelay.group.repository.GroupMemberIndexMapper;
import com.ltcpond.msgrelay.group.service.BitmapAckService;
import com.ltcpond.msgrelay.reliability.model.entity.MsgReadBitmap;
import com.ltcpond.msgrelay.reliability.repository.MsgReadBitmapMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class BitmapAckServiceImpl implements BitmapAckService {

    /** Bitmap 大小：512 字节 = 4096 位，支持 4096 人群聊 */
    private static final int MAX_BITMAP_SIZE = 512;

    @Resource
    private GroupMemberIndexMapper memberIndexMapper;

    @Resource
    private MsgReadBitmapMapper readBitmapMapper;

    @Override
    public void markDelivered(Long msgId, Long userId, Long groupId) {
        // 1. 获取用户位序号
        GroupMemberIndex memberIndex = memberIndexMapper.selectByGroupAndUser(groupId, userId);
        if (memberIndex == null) {
            log.warn("User not in group: userId={}, groupId={}", userId, groupId);
            return;
        }

        int index = memberIndex.getMemberIndex();
        int byteIndex = index / 8;
        int bitOffset = index % 8;

        // 2. 获取或创建 Bitmap
        MsgReadBitmap readBitmap = readBitmapMapper.selectByMsgId(msgId);
        byte[] bitmap;

        if (readBitmap == null) {
            bitmap = new byte[MAX_BITMAP_SIZE];
            MsgReadBitmap record = new MsgReadBitmap();
            record.setMsgId(msgId);
            record.setGroupId(groupId);
            record.setDeliveredBitmap(bitmap);
            record.setDeliveredCount(0);
            record.setReadBitmap(new byte[MAX_BITMAP_SIZE]);
            record.setReadCount(0);
            readBitmapMapper.insert(record);
        } else {
            bitmap = readBitmap.getDeliveredBitmap();
            if (bitmap == null) {
                bitmap = new byte[MAX_BITMAP_SIZE];
            }
        }

        // 3. 检查是否已标记
        boolean alreadyDelivered = (bitmap[byteIndex] & (1 << bitOffset)) != 0;
        if (alreadyDelivered) {
            return;
        }

        // 4. 设置对应位为 1
        bitmap[byteIndex] |= (1 << bitOffset);

        // 5. 更新 Bitmap + 已投递计数
        readBitmapMapper.updateDeliveredBitmap(msgId, bitmap);
        readBitmapMapper.incrementDeliveredCount(msgId);

        log.info("Marked delivered with bitmap: msgId={}, userId={}, index={}", msgId, userId, index);
    }

    @Override
    public void markRead(Long msgId, Long userId, Long groupId) {
        // 1. 获取用户位序号
        GroupMemberIndex memberIndex = memberIndexMapper.selectByGroupAndUser(groupId, userId);
        if (memberIndex == null) {
            log.warn("User not in group: userId={}, groupId={}", userId, groupId);
            return;
        }

        int index = memberIndex.getMemberIndex();
        int byteIndex = index / 8;
        int bitOffset = index % 8;

        // 2. 获取或创建 Bitmap
        MsgReadBitmap readBitmap = readBitmapMapper.selectByMsgId(msgId);
        byte[] bitmap;

        if (readBitmap == null) {
            bitmap = new byte[MAX_BITMAP_SIZE];
            MsgReadBitmap record = new MsgReadBitmap();
            record.setMsgId(msgId);
            record.setGroupId(groupId);
            record.setDeliveredBitmap(new byte[MAX_BITMAP_SIZE]);
            record.setDeliveredCount(0);
            record.setReadBitmap(bitmap);
            record.setReadCount(0);
            readBitmapMapper.insert(record);
        } else {
            bitmap = readBitmap.getReadBitmap();
            if (bitmap == null) {
                bitmap = new byte[MAX_BITMAP_SIZE];
            }
        }

        // 3. 检查是否已标记
        boolean alreadyRead = (bitmap[byteIndex] & (1 << bitOffset)) != 0;
        if (alreadyRead) {
            return;
        }

        // 4. 设置对应位为 1
        bitmap[byteIndex] |= (1 << bitOffset);

        // 5. 更新 Bitmap + 已读计数
        readBitmapMapper.updateReadBitmap(msgId, bitmap);
        readBitmapMapper.incrementReadCount(msgId);

        log.info("Marked read with bitmap: msgId={}, userId={}, index={}", msgId, userId, index);
    }

    @Override
    public List<Long> getDeliveredUsers(Long msgId, Long groupId) {
        MsgReadBitmap readBitmap = readBitmapMapper.selectByMsgId(msgId);
        if (readBitmap == null || readBitmap.getDeliveredBitmap() == null) {
            return new ArrayList<>();
        }
        return decodeBitmap(groupId, readBitmap.getDeliveredBitmap(), true);
    }

    @Override
    public List<Long> getReadUsers(Long msgId, Long groupId) {
        MsgReadBitmap readBitmap = readBitmapMapper.selectByMsgId(msgId);
        if (readBitmap == null || readBitmap.getReadBitmap() == null) {
            return new ArrayList<>();
        }
        return decodeBitmap(groupId, readBitmap.getReadBitmap(), true);
    }

    @Override
    public List<Long> getUnreadUsers(Long msgId, Long groupId) {
        // 1. 获取活跃成员 Bitmap
        byte[] memberBitmap = getActiveMemberBitmap(groupId);

        // 2. 获取已读 Bitmap
        MsgReadBitmap readBitmap = readBitmapMapper.selectByMsgId(msgId);
        byte[] readBytes = (readBitmap != null && readBitmap.getReadBitmap() != null)
                           ? readBitmap.getReadBitmap() : new byte[MAX_BITMAP_SIZE];

        // 3. 计算未读 Bitmap = 成员 AND NOT 已读
        byte[] unreadBitmap = new byte[MAX_BITMAP_SIZE];
        for (int i = 0; i < MAX_BITMAP_SIZE; i++) {
            unreadBitmap[i] = (byte) (memberBitmap[i] & ~readBytes[i]);
        }

        // 4. 解码未读用户
        return decodeBitmap(groupId, unreadBitmap, true);
    }

    @Override
    public int getDeliveredCount(Long msgId) {
        MsgReadBitmap readBitmap = readBitmapMapper.selectByMsgId(msgId);
        return (readBitmap != null && readBitmap.getDeliveredCount() != null)
               ? readBitmap.getDeliveredCount() : 0;
    }

    @Override
    public int getReadCount(Long msgId) {
        MsgReadBitmap readBitmap = readBitmapMapper.selectByMsgId(msgId);
        return (readBitmap != null && readBitmap.getReadCount() != null)
               ? readBitmap.getReadCount() : 0;
    }

    /** 获取活跃成员 Bitmap */
    private byte[] getActiveMemberBitmap(Long groupId) {
        byte[] bitmap = new byte[MAX_BITMAP_SIZE];
        List<GroupMemberIndex> activeMembers = memberIndexMapper.selectActiveByGroupId(groupId);

        for (GroupMemberIndex member : activeMembers) {
            int byteIndex = member.getMemberIndex() / 8;
            int bitOffset = member.getMemberIndex() % 8;
            if (byteIndex < MAX_BITMAP_SIZE) {
                bitmap[byteIndex] |= (1 << bitOffset);
            }
        }

        return bitmap;
    }

    /** 解码 Bitmap，获取用户 ID 列表 */
    private List<Long> decodeBitmap(Long groupId, byte[] bitmap, boolean activeOnly) {
        List<Long> userIds = new ArrayList<>();
        List<GroupMemberIndex> members = activeOnly
            ? memberIndexMapper.selectActiveByGroupId(groupId)
            : memberIndexMapper.selectByGroupId(groupId);

        for (GroupMemberIndex member : members) {
            int byteIndex = member.getMemberIndex() / 8;
            int bitOffset = member.getMemberIndex() % 8;

            if (byteIndex < bitmap.length && (bitmap[byteIndex] & (1 << bitOffset)) != 0) {
                userIds.add(member.getUserId());
            }
        }

        return userIds;
    }
}
