package com.example.asset.modules.check.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;
import com.example.asset.modules.check.dto.AnalysisResult;
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

    /**
     * 分析盘点差异。
     * <p>返回条目 + 结论来源（llm / rule / mixed）；分析服务不可用时回退模板，来源记为 rule。</p>
     */
    AnalysisResult analyzeDiff(Long taskId);

    /**
     * 待处置的差异记录。
     * <p>只返回盘盈/盘亏（diff_handle_status = PENDING）的记录；相符记录不参与处置流程。</p>
     *
     * @param taskId 为 null 时查全部任务
     */
    List<CheckRecord> listPendingDiffs(Long taskId);

    /**
     * 已处置的差异记录（可按任务过滤），用于回看"谁、何时、什么方式、关联哪张单"。
     */
    List<CheckRecord> listHandledDiffs(Long taskId);

    /**
     * 标记一条差异为已处置。
     * <p>只是打标签，不直接改资产状态 —— 资产变更仍走报废/新增审批。
     * 重复处置幂等，不覆盖首次的处置人与时间。</p>
     *
     * @param handleType SCRAP=转报废 ASSET_ADD=补登记 MANUAL=人工确认
     * @param refNo      关联单据号（报废单号 / 新资产编号），可空；人工确认通常没有
     */
    void handleDiff(Long recordId, String handleType, String refNo);

    /**
     * 撤销处置，退回待处置。
     * <p>用于误点。会清空处置方式、时间、处置人与关联单号，避免留下"半截"记录。</p>
     */
    void revokeDiff(Long recordId);

    /**
     * 确认盘点结果。
     * <p>只统计差异并把任务置为已完成，<b>不修改资产台账状态</b> ——
     * 盘亏应走报废单、盘盈应走资产新增登记，资产状态仍由审批流统一变更。</p>
     *
     * @return 本次确认的统计（confirmed / shortage / surplus / matched）
     */
    Map<String, Object> confirmDiff(Long taskId, List<Long> recordIds);
}
