package com.example.asset.modules.approval.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.asset.modules.approval.entity.ApplyOrder;

import java.util.Map;

public interface ApprovalService {

    /** 提交领用/归还申请 */
    void submitApply(ApplyOrder order);

    /** 审批（通过/驳回） */
    void approve(Long orderId, Long approveUserId, String approveUserName, Integer roleId, Long approveUserDeptId, String level, String result, String comment);

    /**
     * 分页查询申请单。
     *
     * <p>数据范围由服务端按角色推导：1=全部（可按院系筛）、2=本院系、3=仅本人。
     * {@code applyUserId} 仅对管理员生效，普通师生传入会被忽略。</p>
     */
    IPage<ApplyOrder> pageOrders(int current, int size, String applyType, String status, Long deptId,
                                 Long applyUserId, Long currentUserId, Long currentUserDeptId, Integer currentUserRoleId);

    /** 查询申请单详情（含审批记录，按角色做数据范围校验） */
    Map<String, Object> getOrderDetail(Long id, Long currentUserId, Long currentUserDeptId, Integer currentUserRoleId);
}
