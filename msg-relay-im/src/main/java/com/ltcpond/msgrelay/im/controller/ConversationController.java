package com.ltcpond.msgrelay.im.controller;

import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.im.model.dto.CreateDirectConversationRequest;
import com.ltcpond.msgrelay.im.model.entity.Conversation;
import com.ltcpond.msgrelay.im.model.vo.MessageVO;
import com.ltcpond.msgrelay.im.service.ConversationService;
import com.ltcpond.msgrelay.im.service.MessageAccessService;
import com.ltcpond.msgrelay.im.service.MessageService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 会话控制器 — 会话列表、标记已读 */
@RestController
@RequestMapping("/api/im/conversation")
public class ConversationController {

    @Resource
    private ConversationService conversationService;

    @Resource
    private MessageAccessService messageAccessService;

    @Resource
    private MessageService messageService;

    /** 创建或获取与指定用户的单聊会话。 */
    @PostMapping("/direct")
    public Result<Conversation> createDirect(@Valid @RequestBody CreateDirectConversationRequest body,
                                              HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(conversationService.createDirectConversation(userId, body.getTargetUserId()));
    }

    /** 获取当前用户的会话列表 */
    @GetMapping("/list")
    public Result<List<Conversation>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(conversationService.listConversations(userId));
    }

    /** 标记会话已读 — 清零未读计数，群聊场景触发批量 ACK */
    @PutMapping("/{conversationId}/read")
    public Result<Void> markRead(@PathVariable Long conversationId,
                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        messageAccessService.assertCanAccessConversation(userId, conversationId);
        conversationService.markRead(userId, conversationId);
        return Result.ok();
    }

    /** 按全局会话 ID 分页查询历史消息。 */
    @GetMapping("/{conversationId}/messages")
    public Result<List<MessageVO>> history(@PathVariable Long conversationId,
                                            @RequestParam(required = false) Long beforeMsgId,
                                            @RequestParam(defaultValue = "50") int limit,
                                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(messageService.queryHistory(userId, conversationId, beforeMsgId, limit));
    }
}
