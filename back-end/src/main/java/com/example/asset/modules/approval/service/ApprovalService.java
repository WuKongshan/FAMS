package com.example.asset.modules.approval.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.asset.modules.approval.entity.ApplyOrder;

import java.util.Map;

public interface ApprovalService {

    /** 提交领用/归还申请 */
    void submitApply(ApplyOrder order);

    /** 审批（通过/驳回） */
    void approve(Long orderId, Long approveUserId, String approveUserName, Integer roleId, Long approveUserDeptId, String level, String result, String comment);

    /** 分页查询申请单（自动根据角色进行院系隔离） */
    IPage<ApplyOrder> pageOrders(int current, int size, String applyType, String status, Long deptId, Long applyUserId, Long currentUserDeptId, Integer currentUserRoleId);

    /** 查询申请单详情（含审批记录，自动根据角色进行院系隔离） */
    Map<String, Object> getOrderDetail(Long id, Long currentUserDeptId, Integer currentUserRoleId);
}
