package com.example.asset.modules.admin.controller;

import com.example.asset.common.annotation.OperationLog;
import com.example.asset.common.result.R;
import com.example.asset.modules.admin.entity.User;
import com.example.asset.modules.admin.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> page(@RequestParam(defaultValue = "1") int current,
                     @RequestParam(defaultValue = "10") int size,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) Long deptId) {
        return R.ok(userService.pageUsers(current, size, keyword, deptId));
    }

    @Operation(summary = "新增用户")
    @OperationLog(module = "用户管理", operation = "新增", description = "新增用户")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> add(@RequestBody User user) {
        userService.addUser(user);
        return R.ok("用户创建成功");
    }

    @Operation(summary = "修改用户")
    @OperationLog(module = "用户管理", operation = "修改", description = "编辑用户信息")
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> update(@RequestBody User user) {
        userService.updateUser(user);
        return R.ok("用户更新成功");
    }

    @Operation(summary = "删除用户")
    @OperationLog(module = "用户管理", operation = "删除", description = "删除用户")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return R.ok("用户删除成功");
    }

    @Operation(summary = "重置密码")
    @OperationLog(module = "用户管理", operation = "重置密码", description = "重置用户密码")
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> params) {
        userService.resetPassword(id, params.get("password"));
        return R.ok("密码重置成功");
    }
}
