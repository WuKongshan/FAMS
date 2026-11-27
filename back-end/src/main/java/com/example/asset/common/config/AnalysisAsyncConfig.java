package com.example.asset.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 分析任务专用线程池。
 *
 * <p>为什么要独立线程池：分析会阻塞几十秒到 200 秒，如果借用 Tomcat 的请求线程，
 * 几个人同时点就能把 200 个线程占满，导致登录、列表等所有接口排队。
 * 放到独立池里，对外表现变成"提交后立刻返回，前端轮询"。
 *
 * <p>容量取舍：核心 2 / 最大 4 / 队列 50。分析是重活（一次几十秒、要调外部 API），
 * 并发开大只会让 DeepSeek 侧限流、且各自更慢。队列满时直接拒绝 —— 用 AbortPolicy
 * 而不是 CallerRunsPolicy，后者会把活退回请求线程，等于又变回同步阻塞。
 */
@Configuration
public class AnalysisAsyncConfig {

    public static final String EXECUTOR_NAME = "analysisExecutor";

    @Bean(EXECUTOR_NAME)
    public ThreadPoolTaskExecutor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 关机时给在跑的分析留 30 秒收尾，避免把任务掐在半路留下 RUNNING 的孤儿行
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
