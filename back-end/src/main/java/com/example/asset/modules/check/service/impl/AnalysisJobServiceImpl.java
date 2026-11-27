package com.example.asset.modules.check.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.asset.common.config.AnalysisAsyncConfig;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.modules.check.dto.AnalysisJobVO;
import com.example.asset.modules.check.dto.AnalysisResult;
import com.example.asset.modules.check.dto.CheckDiffResult;
import com.example.asset.modules.check.entity.AnalysisJob;
import com.example.asset.modules.check.mapper.AnalysisJobMapper;
import com.example.asset.modules.check.service.AnalysisJobService;
import com.example.asset.modules.check.service.CheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 分析任务实现。
 *
 * <p>提交路径只做「插入一行 + 丢给线程池」，不在这里等待任何远程调用；
 * 真正的分析逻辑仍复用 {@link CheckService#analyzeDiff(Long)}，本类只负责调度与状态流转。
 */
@Slf4j
@Service
public class AnalysisJobServiceImpl implements AnalysisJobService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    /** 复用窗口：同一任务在此时间内的进行中作业直接复用，防止双击重复计费 */
    private static final int REUSE_WINDOW_MINUTES = 5;

    /** error_message 列长 500，留出省略号余量 */
    private static final int ERROR_MAX_LENGTH = 480;

    private final AnalysisJobMapper analysisJobMapper;
    private final CheckService checkService;
    private final TaskExecutor analysisExecutor;

    /**
     * 显式构造器（不用 @RequiredArgsConstructor）：
     * Lombok 默认不会把 {@code @Qualifier} 复制到构造参数上，而 Spring Boot 自身也注册了
     * 一个 TaskExecutor（applicationTaskExecutor），按类型注入会歧义、启动即失败。
     */
    public AnalysisJobServiceImpl(AnalysisJobMapper analysisJobMapper,
                                  CheckService checkService,
                                  @Qualifier(AnalysisAsyncConfig.EXECUTOR_NAME) TaskExecutor analysisExecutor) {
        this.analysisJobMapper = analysisJobMapper;
        this.checkService = checkService;
        this.analysisExecutor = analysisExecutor;
    }

    @Override
    public AnalysisJobVO submit(Long taskId) {
        AnalysisJob running = findRunning(taskId);
        if (running != null) {
            log.info("任务 {} 已有进行中的分析作业 {}，直接复用", taskId, running.getId());
            return toVO(running);
        }

        AnalysisJob job = new AnalysisJob();
        job.setTaskId(taskId);
        job.setStatus(STATUS_PENDING);
        analysisJobMapper.insert(job);
        Long jobId = job.getId();

        try {
            analysisExecutor.execute(() -> run(jobId, taskId));
        } catch (TaskRejectedException e) {
            // 队列满：把刚插入的行标成失败，避免留下一条永远 PENDING 的孤儿
            markFailed(jobId, "分析任务排队已满，请稍后重试");
            throw new BusinessException("分析任务排队已满，请稍后重试");
        }

        log.info("分析作业 {} 已提交, 任务ID: {}", jobId, taskId);
        return toVO(job);
    }

    @Override
    public AnalysisJobVO getStatus(Long jobId) {
        AnalysisJob job = analysisJobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException("分析任务不存在");
        }

        AnalysisJobVO vo = toVO(job);
        vo.setEngine(job.getEngine());
        vo.setDegraded("rule".equals(job.getEngine()));
        if (STATUS_SUCCESS.equals(job.getStatus()) && StrUtil.isNotBlank(job.getResultJson())) {
            vo.setResult(JSONUtil.toList(job.getResultJson(), CheckDiffResult.class));
        } else if (STATUS_FAILED.equals(job.getStatus())) {
            vo.setError(StrUtil.blankToDefault(job.getErrorMessage(), "分析失败"));
        }
        return vo;
    }

    /** 线程池里执行的实际工作：置运行中 -> 调分析 -> 写结果或失败原因 */
    private void run(Long jobId, Long taskId) {
        long startedAt = System.currentTimeMillis();
        markRunning(jobId);
        try {
            AnalysisResult result = checkService.analyzeDiff(taskId);

            AnalysisJob update = new AnalysisJob();
            update.setId(jobId);
            update.setStatus(STATUS_SUCCESS);
            update.setResultJson(JSONUtil.toJsonStr(result.getItems()));
            // 记录结论来源：llm-service 不可用时 analyzeDiff 会回退模板并标 rule，
            // 不存这个值，前端无法区分"真分析了"和"静默降级"
            update.setEngine(result.getEngine());
            update.setFinishedTime(LocalDateTime.now());
            analysisJobMapper.updateById(update);

            log.info("分析作业 {} 完成, 任务ID: {}, 结果 {} 条, 引擎: {}, 耗时 {}ms",
                    jobId, taskId, result.getItems().size(), result.getEngine(),
                    System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            // 这里必须吞掉异常：异常一旦抛到线程池外就没人接了，任务会永远停在 RUNNING
            log.error("分析作业 {} 失败, 任务ID: {}", jobId, taskId, e);
            markFailed(jobId, e.getMessage());
        }
    }

    private AnalysisJob findRunning(Long taskId) {
        return analysisJobMapper.selectOne(new LambdaQueryWrapper<AnalysisJob>()
                .eq(AnalysisJob::getTaskId, taskId)
                .in(AnalysisJob::getStatus, STATUS_PENDING, STATUS_RUNNING)
                .ge(AnalysisJob::getCreateTime, LocalDateTime.now().minusMinutes(REUSE_WINDOW_MINUTES))
                .orderByDesc(AnalysisJob::getId)
                .last("LIMIT 1"));
    }

    private void markRunning(Long jobId) {
        AnalysisJob update = new AnalysisJob();
        update.setId(jobId);
        update.setStatus(STATUS_RUNNING);
        analysisJobMapper.updateById(update);
    }

    private void markFailed(Long jobId, String message) {
        AnalysisJob update = new AnalysisJob();
        update.setId(jobId);
        update.setStatus(STATUS_FAILED);
        update.setErrorMessage(StrUtil.maxLength(
                StrUtil.blankToDefault(message, "未知错误"), ERROR_MAX_LENGTH));
        update.setFinishedTime(LocalDateTime.now());
        try {
            analysisJobMapper.updateById(update);
        } catch (Exception e) {
            log.error("分析作业 {} 标记失败时出错", jobId, e);
        }
    }

    private AnalysisJobVO toVO(AnalysisJob job) {
        AnalysisJobVO vo = AnalysisJobVO.of(job);
        vo.setFinished(STATUS_SUCCESS.equals(job.getStatus()) || STATUS_FAILED.equals(job.getStatus()));
        return vo;
    }
}
