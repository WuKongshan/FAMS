package com.example.asset.modules.check.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;
import com.example.asset.modules.check.dto.CheckDiffResult;

import java.util.List;

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

    /** 确认差异并更新台账 */
    void confirmDiff(Long taskId, List<Long> recordIds);
}
