package com.ltcpond.msgrelay.im.controller;

import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.im.model.entity.Conversation;
import com.ltcpond.msgrelay.im.service.ConversationService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 会话控制器 — 会话列表、标记已读 */
@RestController
@RequestMapping("/api/im/conversation")
public class ConversationController {

    @Resource
    private ConversationService conversationService;

    /** 获取当前用户的会话列表 */
    @GetMapping("/list")
    public Result<List<Conversation>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(conversationService.listConversations(userId));
    }

    /** 标记会话已读 — 清零未读计数，群聊场景触发批量 ACK */
    @PutMapping("/read/{targetId}")
    public Result<Void> markRead(@PathVariable Long targetId,
                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        conversationService.markRead(userId, targetId);
        return Result.ok();
    }
}
