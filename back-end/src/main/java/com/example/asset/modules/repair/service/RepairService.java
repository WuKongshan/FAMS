package com.example.asset.modules.repair.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.asset.modules.repair.entity.RepairOrder;

public interface RepairService {

    /** 提交报修记录（deptId/deptName 由 Controller 填充） */
    void submitOrder(RepairOrder order);

    /** 更新报修工单（接单/维修/完成），自动按角色进行院系隔离校验 */
    void updateOrder(RepairOrder order, Long currentUserId, Long currentUserDeptId, Integer currentUserRoleId);

    /** 查询工单详情（含院系隔离校验） */
    RepairOrder getOrderDetail(Long id, Long currentUserDeptId, Integer currentUserRoleId);

    /** 分页查询报修记录（自动根据角色进行院系隔离） */
    IPage<RepairOrder> pageOrders(int current, int size, String status, Long deptId, Long currentUserDeptId, Integer currentUserRoleId);
}
