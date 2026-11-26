package com.example.asset.modules.approval.controller;

import com.example.asset.common.annotation.OperationLog;
import com.example.asset.common.result.R;
import com.example.asset.common.utils.PageUtils;
import com.example.asset.modules.approval.entity.ApplyOrder;
import com.example.asset.modules.approval.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "资产领用与归还审批")
@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @Operation(summary = "提交领用/归还申请")
    @OperationLog(module = "审批管理", operation = "提交申请", description = "提交领用/归还申请")
    @PostMapping("/apply")
    public R<?> apply(@RequestBody ApplyOrder order) {
        // 从 SecurityContext 获取当前用户信息，自动填充部门字段，防止跨院系伪造
        Map<String, Object> userDetails = getUserDetails();
        order.setApplyUserId((Long) userDetails.get("userId"));
        order.setApplyUserName((String) userDetails.get("realName"));
        order.setDeptId(toLong(userDetails.get("deptId")));
        order.setDeptName((String) userDetails.get("deptName"));
        approvalService.submitApply(order);
        return R.ok("申请提交成功");
    }

    @Operation(summary = "审批操作")
    @OperationLog(module = "审批管理", operation = "审批", description = "执行审批操作")
    @PostMapping("/approve")
    public R<?> approve(@RequestBody Map<String, Object> params) {
        Long orderId = Long.valueOf(params.get("orderId").toString());
        String level = params.get("level").toString();
        String result = params.get("result").toString();
        String comment = params.getOrDefault("comment", "").toString();

        // 从 SecurityContext 获取当前登录用户，防止伪造审批人
        Map<String, Object> userDetails = getUserDetails();
        Long approveUserId = (Long) userDetails.get("userId");
        String approveUserName = (String) userDetails.get("realName");
        Integer roleId = (Integer) userDetails.get("roleId");
        Long approveUserDeptId = toLong(userDetails.get("deptId"));

        approvalService.approve(orderId, approveUserId, approveUserName, roleId, approveUserDeptId, level, result, comment);
        return R.ok("审批完成");
    }

    @Operation(summary = "分页查询申请单")
    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") int current,
                     @RequestParam(defaultValue = "10") int size,
                     @RequestParam(required = false) String applyType,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) Long deptId,
                     @RequestParam(required = false) Long applyUserId) {
        // 从 SecurityContext 获取当前用户信息，用于数据范围（院系 / 本人）隔离
        Map<String, Object> userDetails = getUserDetails();
        Long currentUserId = toLong(userDetails.get("userId"));
        Long currentUserDeptId = toLong(userDetails.get("deptId"));
        Integer currentUserRoleId = (Integer) userDetails.get("roleId");
        return R.ok(approvalService.pageOrders(PageUtils.clampCurrent(current), PageUtils.clampSize(size),
                applyType, status, deptId, applyUserId,
                currentUserId, currentUserDeptId, currentUserRoleId));
    }

    /**
     * 从 SecurityContext 获取当前登录用户详情
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getUserDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Map<String, Object>) auth.getDetails();
    }

    /**
     * 安全类型转换：将 Object 转为 Long（兼容 Integer 类型）
     */
    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return Long.valueOf(value.toString());
    }

    @Operation(summary = "查询申请单详情")
    @GetMapping("/{id}")
    public R<?> detail(@PathVariable Long id) {
        // 从 SecurityContext 获取当前用户信息，用于数据范围校验
        Map<String, Object> userDetails = getUserDetails();
        Long currentUserId = toLong(userDetails.get("userId"));
        Long currentUserDeptId = toLong(userDetails.get("deptId"));
        Integer currentUserRoleId = (Integer) userDetails.get("roleId");
        return R.ok(approvalService.getOrderDetail(id, currentUserId, currentUserDeptId, currentUserRoleId));
    }
}
