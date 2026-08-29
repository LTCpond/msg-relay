package com.ltcpond.msgrelay.im.controller;

import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.group.service.BitmapAckService;
import com.ltcpond.msgrelay.im.model.entity.Message;
import com.ltcpond.msgrelay.im.service.MessageService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** 消息可靠性控制器 — ACK 确认（支持单聊和群聊） */
@RestController
@RequestMapping("/api/reliability")
public class AckController {

    @Resource
    private BitmapAckService bitmapAckService;

    @Resource
    private MessageService messageService;

    /** 消息 ACK 确认 — status=3 已读，其他默认已投递 */
    @PostMapping("/ack/{msgId}")
    public Result<Void> ack(@PathVariable Long msgId,
                             @RequestParam(defaultValue = "2") Integer status,
                             HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        // 查询消息信息
        Message message = messageService.getByMsgIdEntity(msgId);
        if (message == null) {
            return Result.fail(ResultCode.NOT_FOUND, "消息不存在");
        }

        if (message.getReceiverType() == 1) {
            // 单聊：更新消息表 status
            if (status == 3) {
                messageService.markRead(msgId);
            } else {
                messageService.markDelivered(msgId);
            }
        } else {
            // 群聊：用 Bitmap
            Long groupId = message.getReceiverId();
            if (status == 3) {
                bitmapAckService.markRead(msgId, userId, groupId);
            } else {
                bitmapAckService.markDelivered(msgId, userId, groupId);
            }
        }

        return Result.ok();
    }
}
