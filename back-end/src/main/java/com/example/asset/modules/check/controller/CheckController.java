package com.example.asset.modules.check.controller;

import com.example.asset.common.annotation.OperationLog;
import com.example.asset.common.result.R;
import com.example.asset.common.utils.PageUtils;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;
import com.example.asset.modules.check.service.AnalysisJobService;
import com.example.asset.modules.check.service.CheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "协同盘点管理")
@RestController
@RequestMapping("/api/check")
@RequiredArgsConstructor
public class CheckController {

    private final CheckService checkService;
    private final AnalysisJobService analysisJobService;

    @Operation(summary = "发布盘点任务")
    @OperationLog(module = "协同盘点", operation = "新增", description = "发布盘点任务")
    @PostMapping("/task")
    public R<?> createTask(@RequestBody CheckTask task) {
        checkService.createTask(task);
        return R.ok("盘点任务发布成功");
    }

    @Operation(summary = "分页查询盘点任务")
    @GetMapping("/task/page")
    public R<?> pageTasks(@RequestParam(defaultValue = "1") int current,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(required = false) String status) {
        return R.ok(checkService.pageTasks(PageUtils.clampCurrent(current), PageUtils.clampSize(size), status));
    }

    @Operation(summary = "录入盘点数据（多人协同）")
    @OperationLog(module = "协同盘点", operation = "录入", description = "录入盘点数据")
    @PostMapping("/record")
    public R<?> saveRecord(@RequestBody CheckRecord record) {
        checkService.saveCheckRecord(record);
        return R.ok(record);  // 返回保存后的记录，包含自增ID
    }

    @Operation(summary = "查询盘点记录列表")
    @GetMapping("/record/{taskId}")
    public R<?> getRecords(@PathVariable Long taskId) {
        return R.ok(checkService.getRecordsByTaskId(taskId));
    }

    @Operation(summary = "提交差异分析（异步，立即返回 jobId）")
    @OperationLog(module = "协同盘点", operation = "分析", description = "LLM智能分析盘点差异")
    @PostMapping("/analyze/{taskId}")
    public R<?> analyzeDiff(@PathVariable Long taskId) {
        return R.ok(analysisJobService.submit(taskId));
    }

    @Operation(summary = "查询分析任务状态")
    @GetMapping("/analyze/job/{jobId}")
    public R<?> getAnalyzeJob(@PathVariable Long jobId) {
        return R.ok(analysisJobService.getStatus(jobId));
    }

    @Operation(summary = "确认盘点结果（不修改资产台账）")
    @OperationLog(module = "协同盘点", operation = "确认", description = "确认盘点结果，差异转由报废/新增流程处置")
    @PostMapping("/confirm/{taskId}")
    public R<?> confirmDiff(@PathVariable Long taskId, @RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Object> rawList = (List<Object>) params.get("recordIds");
        List<Long> recordIds = rawList.stream()
                .map(obj -> obj instanceof Number ? ((Number) obj).longValue() : Long.valueOf(obj.toString()))
                .toList();
        return R.ok(checkService.confirmDiff(taskId, recordIds));
    }
}
