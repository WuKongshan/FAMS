package com.example.asset.modules.check.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;
import com.example.asset.modules.check.dto.CheckDiffResult;

import java.util.List;
import java.util.Map;

public interface CheckService {

    /** 发布盘点任务 */
    void createTask(CheckTask task);

    /** 分页查询盘点任务 */
    IPage<CheckTask> pageTasks(int current, int size, String status);

    /** 录入盘点数据（多人协同） */
    void saveCheckRecord(CheckRecord record);

    /** 查询盘点记录列表 */
    List<CheckRecord> getRecordsByTaskId(Long taskId);

    /** LLM 智能分析差异 */
    List<CheckDiffResult> analyzeDiff(Long taskId);

    /**
     * 确认盘点结果。
     * <p>只统计差异并把任务置为已完成，<b>不修改资产台账状态</b> ——
     * 盘亏应走报废单、盘盈应走资产新增登记，资产状态仍由审批流统一变更。</p>
     *
     * @return 本次确认的统计（confirmed / shortage / surplus / matched）
     */
    Map<String, Object> confirmDiff(Long taskId, List<Long> recordIds);
}
