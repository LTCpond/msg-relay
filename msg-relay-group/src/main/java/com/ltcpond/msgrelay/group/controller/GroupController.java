package com.ltcpond.msgrelay.group.controller;

import com.ltcpond.msgrelay.common.exception.BusinessException;
import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.common.result.ResultCode;
import com.ltcpond.msgrelay.group.model.entity.Group;
import com.ltcpond.msgrelay.group.service.GroupService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/group")
public class GroupController {

    @Resource
    private GroupService groupService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @PostMapping
    public Result<Group> create(@RequestBody Map<String, String> body,
                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String name = body.get("name");

        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "群组名称不能为空");
        }

        String idempotentKey = "idempotent:group:create:" + userId + ":" + name;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", 5, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException(ResultCode.CONFLICT, "群组创建中，请勿重复点击");
        }

        try {
            return Result.ok(groupService.create(userId, name));
        } catch (Exception e) {
            redisTemplate.delete(idempotentKey);
            throw e;
        }
    }

    @GetMapping("/{id}/members")
    public Result<?> listMembers(@PathVariable Long id) {
        return Result.ok(groupService.listMembers(id));
    }

    @GetMapping("/{id}")
    public Result<Group> getGroup(@PathVariable Long id) {
        return Result.ok(groupService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteGroup(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        groupService.deleteGroup(id, userId);
        return Result.ok();
    }

    @PostMapping("/{groupId}/member/{userId}")
    public Result<Void> addMember(@PathVariable Long groupId,
                                  @PathVariable Long userId,
                                  HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        groupService.addMember(groupId, userId, operatorId);
        return Result.ok();
    }

    @DeleteMapping("/{groupId}/member/{userId}")
    public Result<Void> removeMember(@PathVariable Long groupId,
                                     @PathVariable Long userId,
                                     HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        groupService.removeMember(groupId, userId, operatorId);
        return Result.ok();
    }

    @PostMapping("/{groupId}/leave")
    public Result<Void> leaveGroup(@PathVariable Long groupId,
                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        groupService.leaveGroup(groupId, userId);
        return Result.ok();
    }

    @PutMapping("/{groupId}/mute/{userId}")
    public Result<Void> muteMember(@PathVariable Long groupId,
                                   @PathVariable Long userId,
                                   @RequestParam(defaultValue = "10") int minutes,
                                   HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        groupService.muteMember(groupId, userId, minutes, operatorId);
        return Result.ok();
    }

    @PutMapping("/{groupId}/mute-all")
    public Result<Void> muteAll(@PathVariable Long groupId,
                                @RequestParam boolean muted,
                                HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        groupService.muteAll(groupId, muted, operatorId);
        return Result.ok();
    }

    @PutMapping("/{groupId}/role/{userId}")
    public Result<Void> setRole(@PathVariable Long groupId,
                                @PathVariable Long userId,
                                @RequestParam int role,
                                HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        groupService.setRole(groupId, userId, role, operatorId);
        return Result.ok();
    }

    @PutMapping("/{groupId}/owner/{userId}")
    public Result<Void> transferOwner(@PathVariable Long groupId,
                                      @PathVariable Long userId,
                                      HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        groupService.transferOwner(groupId, userId, operatorId);
        return Result.ok();
    }
}
