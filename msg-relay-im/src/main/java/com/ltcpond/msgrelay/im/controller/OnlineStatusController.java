package com.ltcpond.msgrelay.im.controller;

import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.im.service.OnlineStatusService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 在线状态控制器 — 查询用户在线状态 */
@RestController
@RequestMapping("/api/im/online")
public class OnlineStatusController {

    @Resource
    private OnlineStatusService onlineStatusService;

    /** 批量查询用户在线状态 */
    @PostMapping("/status/batch")
    public Result<Map<Long, Boolean>> batchStatus(@RequestBody List<Long> userIds) {
        return Result.ok(onlineStatusService.batchIsOnline(userIds));
    }

    /** 查询单个用户在线状态 */
    @GetMapping("/status/{userId}")
    public Result<Map<String, Object>> singleStatus(@PathVariable Long userId) {
        boolean online = onlineStatusService.isOnline(userId);
        return Result.ok(Map.of("userId", userId, "online", online));
    }
}
