package com.example.asset.modules.check.dto;

import com.example.asset.modules.check.entity.AnalysisJob;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 分析任务的对外视图。
 *
 * <p>轮询接口只返回必要字段：状态 + 结果或错误，不暴露 resultJson 原文。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisJobVO {

    private Long jobId;

    private Long taskId;

    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    /** 仅 SUCCESS 时有值 */
    private List<CheckDiffResult> result;

    /** 仅 FAILED 时有值 */
    private String error;

    /** 结论来源：llm / rule / mixed */
    private String engine;

    /** 是否走了模板兜底（engine=rule）—— 前端据此提示"本次未使用 AI" */
    private boolean degraded;

    /** 供前端判断是否已结束 */
    private boolean finished;

    public static AnalysisJobVO of(AnalysisJob job) {
        AnalysisJobVO vo = new AnalysisJobVO();
        vo.setJobId(job.getId());
        vo.setTaskId(job.getTaskId());
        vo.setStatus(job.getStatus());
        return vo;
    }
}
