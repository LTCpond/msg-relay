package com.ltcpond.msgrelay.user.controller;

import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.user.model.dto.CreateMemberRequest;
import com.ltcpond.msgrelay.user.model.dto.CreateTeamRequest;
import com.ltcpond.msgrelay.user.model.entity.Team;
import com.ltcpond.msgrelay.user.model.entity.TeamAdmin;
import com.ltcpond.msgrelay.user.model.vo.UserVO;
import com.ltcpond.msgrelay.user.service.TeamService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/team")
public class TeamController {

    @Resource
    private TeamService teamService;

    @PostMapping
    public Result<Team> create(@Valid @RequestBody CreateTeamRequest request,
                               HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.ok(teamService.create(userId, request));
    }

    @GetMapping("/{id}")
    public Result<Team> getTeam(@PathVariable Long id) {
        return Result.ok(teamService.getById(id));
    }

    @GetMapping("/mine")
    public Result<Team> mine(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(teamService.getByUserId(userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        teamService.delete(id, userId);
        return Result.ok();
    }

    /** 当前团队所有成员 */
    @GetMapping("/mine/members")
    public Result<List<UserVO>> listMembers(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(teamService.listMembers(userId));
    }

    /** 创建成员（owner/admin） */
    @PostMapping("/mine/members")
    public Result<UserVO> createMember(@Valid @RequestBody CreateMemberRequest req,
                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(teamService.createMember(userId, req));
    }

    /** 开除员工（owner/admin） */
    @DeleteMapping("/mine/members/{memberId}")
    public Result<Void> dismissMember(@PathVariable Long memberId,
                                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        teamService.dismissMember(userId, memberId);
        return Result.ok();
    }

    /** 添加管理员（仅 owner） */
    @PostMapping("/mine/admins")
    public Result<Void> addAdmin(@RequestParam Long userId,
                                 HttpServletRequest request) {
        Long ownerId = (Long) request.getAttribute("userId");
        teamService.addAdmin(ownerId, userId);
        return Result.ok();
    }

    /** 管理员列表 */
    @GetMapping("/mine/admins")
    public Result<List<TeamAdmin>> listAdmins(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(teamService.listAdmins(userId));
    }

    /** 转让团队（仅 owner） */
    @PutMapping("/mine/owner")
    public Result<Void> transferOwnership(@RequestParam Long userId,
                                          HttpServletRequest request) {
        Long ownerId = (Long) request.getAttribute("userId");
        teamService.transferOwnership(ownerId, userId);
        return Result.ok();
    }

    /** 移除管理员（仅 owner） */
    @DeleteMapping("/mine/admins/{userId}")
    public Result<Void> removeAdmin(@PathVariable Long userId,
                                    HttpServletRequest request) {
        Long ownerId = (Long) request.getAttribute("userId");
        teamService.removeAdmin(ownerId, userId);
        return Result.ok();
    }
}
