package com.example.asset.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.modules.admin.entity.User;
import com.example.asset.modules.admin.mapper.UserMapper;
import com.example.asset.modules.admin.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public IPage<User> pageUsers(int current, int size, String keyword, Long deptId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword)
                    .or().like(User::getPhone, keyword));
        }
        if (deptId != null) {
            wrapper.eq(User::getDeptId, deptId);
        }
        wrapper.orderByDesc(User::getCreateTime);
        return page(new Page<>(current, size), wrapper);
    }

    @Override
    public Map<String, Object> stats() {
        // 用 count 而不是把用户全查出来再 stream 过滤：
        // 各角色人数由数据库聚合，内存占用与用户总量无关。
        Map<String, Object> result = new HashMap<>();
        result.put("total", count());
        result.put("adminCount", count(new LambdaQueryWrapper<User>().eq(User::getRoleId, 1L)));
        result.put("deptAdminCount", count(new LambdaQueryWrapper<User>().eq(User::getRoleId, 2L)));
        result.put("userCount", count(new LambdaQueryWrapper<User>().eq(User::getRoleId, 3L)));
        return result;
    }

    @Override
    public void addUser(User user) {
        // 检查用户名唯一性
        User existUser = baseMapper.selectByUsername(user.getUsername());
        if (existUser != null) {
            throw new BusinessException("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        save(user);
    }

    @Override
    public void updateUser(User user) {
        User exist = getById(user.getId());
        if (exist == null) {
            throw new BusinessException("用户不存在");
        }
        // 不修改密码
        user.setPassword(null);
        updateById(user);
    }

    @Override
    public void deleteUser(Long id) {
        removeById(id);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        updateById(user);
    }
}
