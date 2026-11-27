package com.example.asset.modules.check.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.modules.check.dto.AnalysisJobVO;
import com.example.asset.modules.check.dto.CheckDiffResult;
import com.example.asset.modules.check.entity.AnalysisJob;
import com.example.asset.modules.check.mapper.AnalysisJobMapper;
import com.example.asset.modules.check.service.CheckService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分析任务（异步）的核心不变量：
 * <b>提交路径必须只做 insert + 丢线程池，绝不在请求线程里等待分析结果。</b>
 *
 * <p>测试里把线程池换成"立刻在当前线程执行"，既保留了 run() 的真实逻辑，
 * 又让断言是确定性的（不需要 sleep 等异步）。
 */
@ExtendWith(MockitoExtension.class)
class AnalysisJobServiceTest {

    private static final Long TASK_ID = 7L;
    private static final Long JOB_ID = 99L;

    @Mock private AnalysisJobMapper analysisJobMapper;
    @Mock private CheckService checkService;
    @Mock private TaskExecutor analysisExecutor;

    private AnalysisJobServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析实体字段需要 TableInfo，单测里没有 Spring 容器，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AnalysisJob.class);
    }

    private AnalysisJobServiceImpl newService() {
        return new AnalysisJobServiceImpl(analysisJobMapper, checkService, analysisExecutor);
    }

    /** insert 时回填主键，并让线程池同步执行（等价于线上"异步跑完"之后的最终状态） */
    private void givenInlineExecutor() {
        doAnswer(inv -> {
            AnalysisJob job = inv.getArgument(0);
            job.setId(JOB_ID);
            return 1;
        }).when(analysisJobMapper).insert(any(AnalysisJob.class));

        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(analysisExecutor).execute(any(Runnable.class));
    }

    private static CheckDiffResult diffResult(long recordId) {
        CheckDiffResult r = new CheckDiffResult();
        r.setRecordId(recordId);
        r.setAssetName("资产" + recordId);
        r.setAnalysis("模型分析" + recordId);
        r.setSuggestion("模型建议" + recordId);
        return r;
    }

    @Test
    @DisplayName("提交：插入 PENDING 行并立刻返回，不等分析结果")
    void submit_returnsImmediatelyWithPending() {
        givenInlineExecutor();
        when(checkService.analyzeDiff(TASK_ID)).thenReturn(List.of(diffResult(1)));
        service = newService();

        AnalysisJobVO vo = service.submit(TASK_ID);

        // 返回给调用方的是"已受理"，不是分析结果
        assertThat(vo.getStatus()).isEqualTo(AnalysisJobServiceImpl.STATUS_PENDING);
        assertThat(vo.getJobId()).isEqualTo(JOB_ID);
        assertThat(vo.isFinished()).isFalse();
        assertThat(vo.getResult()).isNull();
        verify(analysisExecutor).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("分析成功：状态流转 RUNNING -> SUCCESS，结果写入 result_json")
    void run_success_persistsResult() {
        givenInlineExecutor();
        when(checkService.analyzeDiff(TASK_ID)).thenReturn(List.of(diffResult(1), diffResult(2)));
        service = newService();

        service.submit(TASK_ID);

        ArgumentCaptor<AnalysisJob> captor = ArgumentCaptor.forClass(AnalysisJob.class);
        verify(analysisJobMapper, times(2)).updateById(captor.capture());
        List<AnalysisJob> updates = captor.getAllValues();

        assertThat(updates.get(0).getStatus()).isEqualTo(AnalysisJobServiceImpl.STATUS_RUNNING);
        AnalysisJob finished = updates.get(1);
        assertThat(finished.getStatus()).isEqualTo(AnalysisJobServiceImpl.STATUS_SUCCESS);
        assertThat(finished.getFinishedTime()).isNotNull();
        assertThat(finished.getResultJson()).contains("\"recordId\":1").contains("模型分析2");
    }

    @Test
    @DisplayName("分析抛异常：置 FAILED 并记录原因，异常不外抛")
    void run_failure_marksFailed() {
        givenInlineExecutor();
        when(checkService.analyzeDiff(TASK_ID)).thenThrow(new RuntimeException("分析服务不可用"));
        service = newService();

        // 关键：不能把异常抛给提交方，否则用户看到的是 500 而不是"任务失败"
        AnalysisJobVO vo = service.submit(TASK_ID);
        assertThat(vo.getStatus()).isEqualTo(AnalysisJobServiceImpl.STATUS_PENDING);

        ArgumentCaptor<AnalysisJob> captor = ArgumentCaptor.forClass(AnalysisJob.class);
        verify(analysisJobMapper, times(2)).updateById(captor.capture());
        AnalysisJob failed = captor.getAllValues().get(1);
        assertThat(failed.getStatus()).isEqualTo(AnalysisJobServiceImpl.STATUS_FAILED);
        assertThat(failed.getErrorMessage()).contains("分析服务不可用");
    }

    @Test
    @DisplayName("线程池队列满：标记失败并抛业务异常，不留 PENDING 孤儿")
    void submit_rejected_marksFailed() {
        doAnswer(inv -> {
            AnalysisJob job = inv.getArgument(0);
            job.setId(JOB_ID);
            return 1;
        }).when(analysisJobMapper).insert(any(AnalysisJob.class));
        doThrow(new TaskRejectedException("full")).when(analysisExecutor).execute(any(Runnable.class));
        service = newService();

        assertThatThrownBy(() -> service.submit(TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排队已满");

        ArgumentCaptor<AnalysisJob> captor = ArgumentCaptor.forClass(AnalysisJob.class);
        verify(analysisJobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AnalysisJobServiceImpl.STATUS_FAILED);
    }

    @Test
    @DisplayName("同一任务 5 分钟内有进行中作业：复用，不重复调用模型")
    void submit_reusesRunningJob() {
        AnalysisJob running = new AnalysisJob();
        running.setId(JOB_ID);
        running.setTaskId(TASK_ID);
        running.setStatus(AnalysisJobServiceImpl.STATUS_RUNNING);
        when(analysisJobMapper.selectOne(any())).thenReturn(running);
        service = newService();

        AnalysisJobVO vo = service.submit(TASK_ID);

        assertThat(vo.getJobId()).isEqualTo(JOB_ID);
        assertThat(vo.getStatus()).isEqualTo(AnalysisJobServiceImpl.STATUS_RUNNING);
        verify(analysisJobMapper, never()).insert(any(AnalysisJob.class));
        verify(analysisExecutor, never()).execute(any(Runnable.class));
        verify(checkService, never()).analyzeDiff(any());
    }

    @Test
    @DisplayName("查询成功作业：结果被反序列化回 CheckDiffResult 列表")
    void getStatus_success_returnsDeserializedResult() {
        AnalysisJob job = new AnalysisJob();
        job.setId(JOB_ID);
        job.setTaskId(TASK_ID);
        job.setStatus(AnalysisJobServiceImpl.STATUS_SUCCESS);
        job.setResultJson("[{\"recordId\":1,\"analysis\":\"a1\",\"suggestion\":\"s1\"}]");
        when(analysisJobMapper.selectById(JOB_ID)).thenReturn(job);
        service = newService();

        AnalysisJobVO vo = service.getStatus(JOB_ID);

        assertThat(vo.isFinished()).isTrue();
        assertThat(vo.getResult()).hasSize(1);
        assertThat(vo.getResult().get(0).getAnalysis()).isEqualTo("a1");
        assertThat(vo.getError()).isNull();
    }

    @Test
    @DisplayName("查询失败作业：带回错误原因")
    void getStatus_failed_returnsError() {
        AnalysisJob job = new AnalysisJob();
        job.setId(JOB_ID);
        job.setStatus(AnalysisJobServiceImpl.STATUS_FAILED);
        job.setErrorMessage("分析服务不可用");
        when(analysisJobMapper.selectById(JOB_ID)).thenReturn(job);
        service = newService();

        AnalysisJobVO vo = service.getStatus(JOB_ID);

        assertThat(vo.isFinished()).isTrue();
        assertThat(vo.getError()).isEqualTo("分析服务不可用");
        assertThat(vo.getResult()).isNull();
    }

    @Test
    @DisplayName("查询不存在的作业：抛业务异常而不是返回 null")
    void getStatus_notFound_throws() {
        when(analysisJobMapper.selectById(404L)).thenReturn(null);
        service = newService();

        assertThatThrownBy(() -> service.getStatus(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }
}
