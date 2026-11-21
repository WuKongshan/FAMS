package com.example.asset.modules.admin.controller;

import com.example.asset.common.annotation.OperationLog;
import com.example.asset.common.result.R;
import com.example.asset.modules.admin.dto.LoginDTO;
import com.example.asset.modules.admin.dto.RegisterDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.asset.modules.admin.entity.User;
import com.example.asset.modules.admin.mapper.DepartmentMapper;
import com.example.asset.modules.admin.mapper.UserMapper;
import com.example.asset.modules.admin.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;

    @Operation(summary = "用户登录")
    @OperationLog(module = "认证管理", operation = "登录", description = "用户登录")
    @PostMapping("/login")
    public R<?> login(@Valid @RequestBody LoginDTO loginDTO) {
        return R.ok(authService.login(loginDTO));
    }

    @Operation(summary = "用户注册")
    @OperationLog(module = "认证管理", operation = "注册", description = "用户注册")
    @PostMapping("/register")
    public R<?> register(@Valid @RequestBody RegisterDTO registerDTO) {
        return R.ok(authService.register(registerDTO));
    }

    @Operation(summary = "获取部门列表（公开）")
    @GetMapping("/departments")
    public R<?> departments() {
        return R.ok(departmentMapper.selectList(null));
    }

    @Operation(summary = "获取用户列表（仅学院管理员，用于下拉选择）")
    @GetMapping("/users")
    public R<?> users() {
        return R.ok(userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRoleId, 2L)));
    }
}
