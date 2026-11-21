package com.example.asset.modules.statistics.controller;

import com.example.asset.common.annotation.OperationLog;
import com.example.asset.common.result.R;
import com.example.asset.modules.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "数据统计与报表")
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @OperationLog(module = "数据统计", operation = "查询", description = "各学院资产数量统计", recordParams = false)
    @Operation(summary = "各学院资产数量统计")
    @GetMapping("/dept-assets")
    public R<?> deptAssetStats() {
        return R.ok(statisticsService.deptAssetStats());
    }

    @OperationLog(module = "数据统计", operation = "查询", description = "资产类型分布统计", recordParams = false)
    @Operation(summary = "资产类型分布统计")
    @GetMapping("/category")
    public R<?> categoryStats() {
        return R.ok(statisticsService.categoryStats());
    }

    @OperationLog(module = "数据统计", operation = "查询", description = "资产状态统计", recordParams = false)
    @Operation(summary = "资产状态统计")
    @GetMapping("/status")
    public R<?> statusStats() {
        return R.ok(statisticsService.statusStats());
    }

    @OperationLog(module = "数据统计", operation = "查询", description = "盘点差异统计", recordParams = false)
    @Operation(summary = "盘点差异统计")
    @GetMapping("/check-diff")
    public R<?> checkDiffStats(@RequestParam(required = false) Long taskId) {
        return R.ok(statisticsService.checkDiffStats(taskId));
    }
}
