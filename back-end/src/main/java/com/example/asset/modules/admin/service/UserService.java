package com.example.asset.modules.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.asset.modules.admin.entity.User;

public interface UserService extends IService<User> {

    IPage<User> pageUsers(int current, int size, String keyword, Long deptId);

    void addUser(User user);

    void updateUser(User user);

    void deleteUser(Long id);

    void resetPassword(Long id, String newPassword);
}
