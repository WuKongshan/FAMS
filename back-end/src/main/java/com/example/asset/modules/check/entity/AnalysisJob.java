package com.example.asset.modules.check.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 盘点差异分析任务（异步）。
 *
 * <p>提交时插入一行 PENDING，线程池开始执行时置 RUNNING，结束置 SUCCESS / FAILED。
 * 前端凭 id 轮询状态，避免同步接口长时间占住 Tomcat 线程。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("analysis_job")
public class AnalysisJob extends BaseEntity {

    /** 盘点任务ID */
    private Long taskId;

    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    /** 成功时的分析结果（CheckDiffResult 数组的 JSON） */
    private String resultJson;

    /** 失败原因 */
    private String errorMessage;

    /**
     * 结论来源：llm / rule / mixed。
     * 用于识别"静默降级"—— llm-service 不可用时 Java 会回退模板文案，
     * 作业仍是 SUCCESS，只看状态无法判断结论可信度。
     */
    private String engine;

    /** 结束时间（成功或失败） */
    private LocalDateTime finishedTime;
}
