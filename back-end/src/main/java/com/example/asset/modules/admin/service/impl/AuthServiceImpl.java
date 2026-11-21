package com.example.asset.modules.admin.service.impl;

import com.example.asset.common.exception.BusinessException;
import com.example.asset.common.utils.JwtUtils;
import com.example.asset.modules.admin.dto.LoginDTO;
import com.example.asset.modules.admin.dto.LoginResult;
import com.example.asset.modules.admin.dto.RegisterDTO;
import com.example.asset.modules.admin.entity.Role;
import com.example.asset.modules.admin.entity.User;
import com.example.asset.modules.admin.mapper.RoleMapper;
import com.example.asset.modules.admin.mapper.UserMapper;
import com.example.asset.modules.admin.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Override
    public LoginResult login(LoginDTO loginDTO) {
        try {
            User user = userMapper.selectByUsername(loginDTO.getUsername());
            if (user == null) {
                throw new BusinessException("用户名或密码错误");
            }
            if (user.getStatus() == 0) {
                throw new BusinessException("用户已被禁用，请联系管理员");
            }
            if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
                throw new BusinessException("用户名或密码错误");
            }

            // 查询实际角色编码
            Role role = roleMapper.selectById(user.getRoleId());
            String roleCode = role != null ? role.getRoleCode() : "ROLE_USER";

            // 生成 Token（含院系信息，用于后续院系隔离校验）
            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", List.of(roleCode));
            claims.put("realName", user.getRealName());
            claims.put("deptId", user.getDeptId());
            claims.put("deptName", user.getDeptName());
            claims.put("roleId", user.getRoleId());
            String token = jwtUtils.generateToken(user.getId(), user.getUsername(), claims);

            LoginResult result = new LoginResult();
            result.setToken(token);
            result.setUserId(user.getId());
            result.setUsername(user.getUsername());
            result.setRealName(user.getRealName());
            result.setRoleId(user.getRoleId());
            result.setDeptId(user.getDeptId());
            result.setDeptName(user.getDeptName());
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("登录处理失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    @Override
    public LoginResult register(RegisterDTO registerDTO) {
        // 检查用户名唯一性
        User existUser = userMapper.selectByUsername(registerDTO.getUsername());
        if (existUser != null) {
            throw new BusinessException("用户名已存在，请更换用户名");
        }

        // 创建新用户，默认角色为普通师生（roleId=3），状态为启用
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRealName(registerDTO.getRealName());
        user.setPhone(registerDTO.getPhone());
        user.setEmail(registerDTO.getEmail());
        user.setDeptId(registerDTO.getDeptId());
        user.setDeptName(registerDTO.getDeptName());
        user.setRoleId(3L);  // ROLE_USER
        user.setStatus(1);   // 启用

        // 新注册用户统一为普通师生角色
        userMapper.insert(user);

        // 注册成功后自动登录，生成 Token（含院系信息）
        Role role = roleMapper.selectById(3L);
        String roleCode = role != null ? role.getRoleCode() : "ROLE_USER";

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", List.of(roleCode));
        claims.put("realName", user.getRealName());
        claims.put("deptId", user.getDeptId());
        claims.put("deptName", user.getDeptName());
        claims.put("roleId", user.getRoleId());
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), claims);

        LoginResult result = new LoginResult();
        result.setToken(token);
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setRealName(user.getRealName());
        result.setRoleId(user.getRoleId());
        result.setDeptId(user.getDeptId());
        result.setDeptName(user.getDeptName());
        return result;
    }
}
