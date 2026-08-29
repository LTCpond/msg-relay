package com.ltcpond.msgrelay.user.controller;

import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.user.model.dto.UserUpdateDTO;
import com.ltcpond.msgrelay.user.model.vo.UserVO;
import com.ltcpond.msgrelay.user.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping("/profile")
    public Result<UserVO> profile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userService.getById(userId));
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UserUpdateDTO dto,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.update(userId, dto);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    /** 搜索同团队用户 — 按工号/昵称模糊匹配 */
    @GetMapping("/search")
    public Result<List<UserVO>> search(@RequestParam String keyword,
                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserVO currentUser = userService.getById(userId);
        return Result.ok(userService.searchByKeyword(currentUser.getTeamId(), keyword));
    }
}
