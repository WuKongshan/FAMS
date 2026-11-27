package com.example.asset.modules.check.service;

import com.example.asset.modules.check.dto.AnalysisJobVO;

/**
 * 盘点差异分析任务（异步）。
 *
 * <p>提交后立即返回 jobId，实际分析在独立线程池执行，调用方轮询 {@link #getStatus} 取结果。
 * 这样长耗时的远程调用不再占住 Tomcat 线程。
 */
public interface AnalysisJobService {

    /**
     * 提交一次分析。若该任务已有进行中的作业（5 分钟内），直接复用，不重复调用模型。
     */
    AnalysisJobVO submit(Long taskId);

    /** 查询任务状态；SUCCESS 时带上结果，FAILED 时带上原因 */
    AnalysisJobVO getStatus(Long jobId);
}
