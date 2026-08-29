package com.ltcpond.msgrelay.user.controller;

import com.ltcpond.msgrelay.common.result.Result;
import com.ltcpond.msgrelay.user.model.dto.CreateDeptRequest;
import com.ltcpond.msgrelay.user.model.vo.DepartmentVO;
import com.ltcpond.msgrelay.user.model.vo.UserVO;
import com.ltcpond.msgrelay.user.service.DepartmentService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/dept")
public class DepartmentController {

    @Resource
    private DepartmentService departmentService;

    @PostMapping
    public Result<DepartmentVO> create(@Valid @RequestBody CreateDeptRequest request) {
        return Result.ok(departmentService.create(request));
    }

    @GetMapping("/list/{teamId}")
    public Result<List<DepartmentVO>> listByTeamId(@PathVariable Long teamId) {
        return Result.ok(departmentService.listByTeamId(teamId));
    }

    @GetMapping("/mine")
    public Result<DepartmentVO> mine(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(departmentService.getByUserId(userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        departmentService.delete(id, userId);
        return Result.ok();
    }

    /** 指定部门的所有成员 */
    @GetMapping("/{deptId}/members")
    public Result<List<UserVO>> listMembers(@PathVariable Long deptId) {
        return Result.ok(departmentService.listMembers(deptId));
    }

    /** 更换部门（owner/admin） */
    @PutMapping("/members/{memberId}")
    public Result<Void> changeDept(@PathVariable Long memberId, @RequestParam Long deptId,
                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        departmentService.changeDept(memberId, deptId, userId);
        return Result.ok();
    }
}
