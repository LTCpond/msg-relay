package com.ltcpond.msgrelay.im.controller;

import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.im.model.dto.SendMessageRequest;
import com.ltcpond.msgrelay.im.model.vo.MessageVO;
import com.ltcpond.msgrelay.im.model.vo.ReadStatusVO;
import com.ltcpond.msgrelay.im.service.MessageService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/** 消息控制器 — 消息发送、撤回、隐藏、历史查询、已读状态 */
@RestController
@RequestMapping("/api/im/message")
public class MessageController {

    @Resource
    private MessageService messageService;

    /** 发送消息 — clientMsgId + 数据库唯一键保证网络重试返回同一条消息 */
    @PostMapping("/send")
    public Result<MessageVO> send(@Valid @RequestBody SendMessageRequest request,
                                   HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");

        return Result.ok(messageService.send(userId, request));
    }

    /** 撤回消息 — 仅发送者可撤回，2 小时内有效 */
    @PostMapping("/recall/{msgId}")
    public Result<Void> recall(@PathVariable Long msgId,
                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        messageService.recall(userId, msgId);
        return Result.ok();
    }

    /** 删除消息 — 仅对当前用户不可见（逻辑删除） */
    @PostMapping("/hide/{msgId}")
    public Result<Void> hideMessage(@PathVariable Long msgId,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        messageService.hideMessage(userId, msgId);
        return Result.ok();
    }

    /** 查询历史消息 — 分页拉取，自动过滤隐藏消息，支持单聊和群聊 */
    @GetMapping("/history/{targetId}")
    public Result<List<MessageVO>> history(@PathVariable Long targetId,
                                            @RequestParam Integer receiverType,
                                            @RequestParam(required = false) Long beforeMsgId,
                                            @RequestParam(defaultValue = "50") int limit,
                                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(messageService.queryHistory(userId, targetId, receiverType, beforeMsgId, limit));
    }

    /** 查询消息已读状态 — 仅会话参与者可查看 */
    @GetMapping("/{msgId}/read-status")
    public Result<ReadStatusVO> readStatus(@PathVariable Long msgId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(messageService.getReadStatus(userId, msgId));
    }
}
