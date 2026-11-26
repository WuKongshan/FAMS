package com.example.asset.modules.admin.controller;

import com.example.asset.common.result.R;
import com.example.asset.common.utils.PageUtils;
import com.example.asset.modules.admin.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 操作审计日志控制器 - 仅超级管理员可查看
 */
@Tag(name = "操作审计日志")
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "分页查询操作审计日志")
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> page(@RequestParam(defaultValue = "1") int current,
                     @RequestParam(defaultValue = "10") int size,
                     @RequestParam(required = false) String module,
                     @RequestParam(required = false) String operation,
                     @RequestParam(required = false) String operatorName,
                     @RequestParam(required = false) Integer status,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.ok(operationLogService.pageLogs(PageUtils.clampCurrent(current), PageUtils.clampSize(size),
                module, operation, operatorName, status, startTime, endTime));
    }

    @Operation(summary = "查询操作日志详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> detail(@PathVariable Long id) {
        return R.ok(operationLogService.getLogDetail(id));
    }
}
