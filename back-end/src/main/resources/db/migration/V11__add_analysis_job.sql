-- ============================================
-- Flyway V11: 新增分析任务表 analysis_job
-- ============================================
-- 背景：原 analyzeDiff 是同步接口，Java 侧读取超时 200s，期间 Tomcat 线程被占住。
-- 十几个人同时点「LLM 智能分析」，线程池就会被耗尽，其他接口全部排队。
--
-- 改成异步后，提交请求只做一次 insert 就返回 jobId，实际分析在独立线程池里跑，
-- 前端轮询状态。这张表就是任务状态的唯一真相来源。
--
-- 为什么落表而不是放内存：
--   1. 服务重启后内存任务全丢，前端会永久轮询一个不存在的 jobId；
--   2. 分析结论本身有审计价值（谁在什么时候分析了哪个盘点任务）。
--
-- 状态机：PENDING -> RUNNING -> SUCCESS / FAILED
-- 未做超时清理：若进程在 RUNNING 期间被杀，该行会停在 RUNNING，
-- 由前端轮询超时兜底（提示「分析超时，请重试」），不会无限等待。

CREATE TABLE IF NOT EXISTS analysis_job (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id        BIGINT       NOT NULL COMMENT '盘点任务ID',
    status         VARCHAR(20)  NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    result_json    LONGTEXT     NULL COMMENT '成功时的分析结果（CheckDiffResult 数组的 JSON）',
    error_message  VARCHAR(500) NULL COMMENT '失败原因',
    finished_time  DATETIME     NULL COMMENT '结束时间（成功或失败）',
    create_time    DATETIME     NULL COMMENT '创建时间',
    update_time    DATETIME     NULL COMMENT '更新时间',
    create_by      BIGINT       NULL COMMENT '创建人',
    update_by      BIGINT       NULL COMMENT '更新人',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0=正常 1=已删除）',
    PRIMARY KEY (id),
    KEY idx_analysis_job_task (task_id),
    KEY idx_analysis_job_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='盘点差异分析任务（异步）';
