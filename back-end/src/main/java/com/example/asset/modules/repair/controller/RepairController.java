package com.example.asset.modules.repair.controller;

import com.example.asset.common.annotation.OperationLog;
import com.example.asset.common.result.R;
import com.example.asset.modules.repair.entity.RepairOrder;
import com.example.asset.modules.repair.service.RepairService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "报修记录管理")
@RestController
@RequestMapping("/api/repair")
@RequiredArgsConstructor
public class RepairController {

    private final RepairService repairService;

    @Operation(summary = "提交报修记录")
    @OperationLog(module = "报修管理", operation = "新增", description = "提交报修记录")
    @PostMapping("/order")
    public R<?> submitOrder(@RequestBody RepairOrder order) {
        // 从 SecurityContext 获取当前用户信息，自动填充部门字段，防止跨院系伪造
        Map<String, Object> userDetails = getUserDetails();
        order.setDeptId(toLong(userDetails.get("deptId")));
        order.setDeptName((String) userDetails.get("deptName"));
        repairService.submitOrder(order);
        return R.ok("报修记录提交成功");
    }

    @Operation(summary = "更新报修工单（接单/维修/完成）")
    @OperationLog(module = "报修管理", operation = "更新", description = "更新报修工单状态")
    @PutMapping("/order")
    public R<?> updateOrder(@RequestBody RepairOrder order) {
        // 从 SecurityContext 获取当前登录用户，防止伪造操作人
        Map<String, Object> userDetails = getUserDetails();
        Long currentUserId = toLong(userDetails.get("userId"));
        Long currentUserDeptId = toLong(userDetails.get("deptId"));
        Integer currentUserRoleId = (Integer) userDetails.get("roleId");
        repairService.updateOrder(order, currentUserId, currentUserDeptId, currentUserRoleId);
        return R.ok("工单更新成功");
    }

    @Operation(summary = "查询工单详情")
    @GetMapping("/order/{id}")
    public R<?> detail(@PathVariable Long id) {
        // 从 SecurityContext 获取当前用户信息，用于院系隔离校验
        Map<String, Object> userDetails = getUserDetails();
        Long currentUserDeptId = toLong(userDetails.get("deptId"));
        Integer currentUserRoleId = (Integer) userDetails.get("roleId");
        return R.ok(repairService.getOrderDetail(id, currentUserDeptId, currentUserRoleId));
    }

    @Operation(summary = "分页查询报修记录")
    @GetMapping("/order/page")
    public R<?> pageOrders(@RequestParam(defaultValue = "1") int current,
                           @RequestParam(defaultValue = "10") int size,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) Long deptId) {
        // 从 SecurityContext 获取当前用户信息，用于院系隔离
        Map<String, Object> userDetails = getUserDetails();
        Long currentUserDeptId = toLong(userDetails.get("deptId"));
        Integer currentUserRoleId = (Integer) userDetails.get("roleId");
        return R.ok(repairService.pageOrders(current, size, status, deptId, currentUserDeptId, currentUserRoleId));
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
}
