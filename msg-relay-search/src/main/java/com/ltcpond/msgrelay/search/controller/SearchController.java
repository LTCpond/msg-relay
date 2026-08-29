package com.ltcpond.msgrelay.search.controller;

import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.search.service.SearchService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 消息搜索接口 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Resource
    private SearchService searchService;

    /** 在指定会话内搜索消息，自动过滤当前用户已隐藏的消息 */
    @GetMapping("/message")
    public Result<List<Map<String, Object>>> search(@RequestParam String keyword,
                                                     @RequestParam Long receiverId,
                                                     @RequestParam int receiverType,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(searchService.search(keyword, receiverId, receiverType, userId, page, size));
    }
}
