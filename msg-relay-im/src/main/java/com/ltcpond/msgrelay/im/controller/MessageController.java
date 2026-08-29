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

import org.springframework.data.redis.core.RedisTemplate;
import java.util.concurrent.TimeUnit;
import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.ResultCode;

/** 消息控制器 — 消息发送、撤回、隐藏、历史查询、已读状态 */
@RestController
@RequestMapping("/api/im/message")
public class MessageController {

    @Resource
    private MessageService messageService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /** 发送消息 — 基于内容指纹的 Redis 防抖 1 秒，执行失败自动释放锁 */
    @PostMapping("/send")
    public Result<MessageVO> send(@Valid @RequestBody SendMessageRequest request,
                                   HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");

        // 用内容字段的确定性指纹替代 request.hashCode()
        int contentFingerprint = java.util.Objects.hash(
                request.getReceiverId(), request.getReceiverType(), request.getContent());
        String idempotentKey = "idempotent:msg:send:" + userId + ":" + contentFingerprint;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", 1, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException(ResultCode.CONFLICT, "发送过于频繁，请稍后再试");
        }

        try {
            return Result.ok(messageService.send(userId, request));
        } catch (Exception e) {
            redisTemplate.delete(idempotentKey);
            throw e;
        }
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

    /** 查询消息已读状态 — 群聊展示已读/未读成员列表 */
    @GetMapping("/{msgId}/read-status")
    public Result<ReadStatusVO> readStatus(@PathVariable Long msgId) {
        return Result.ok(messageService.getReadStatus(msgId));
    }
}
