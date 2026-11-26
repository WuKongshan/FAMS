package com.example.asset.modules.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.asset.modules.admin.entity.User;

import java.util.Map;

public interface UserService extends IService<User> {

    IPage<User> pageUsers(int current, int size, String keyword, Long deptId);

    /**
     * 用户概览统计（总数 + 各角色人数）。
     * 单独提供接口，避免前端为了算统计去请求一个超大的分页，
     * 那既是反模式，也会被分页上限截断而算出错误结果。
     */
    Map<String, Object> stats();

    void addUser(User user);

    void updateUser(User user);

    void deleteUser(Long id);

    void resetPassword(Long id, String newPassword);
}
