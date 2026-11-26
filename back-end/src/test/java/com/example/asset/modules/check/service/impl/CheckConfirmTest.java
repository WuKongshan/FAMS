package com.example.asset.modules.check.service.impl;

import com.example.asset.common.llm.LLMService;
import com.example.asset.modules.admin.mapper.DepartmentMapper;
import com.example.asset.modules.admin.mapper.UserMapper;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;
import com.example.asset.modules.check.mapper.CheckRecordMapper;
import com.example.asset.modules.check.mapper.CheckTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 盘点确认动作的回归测试。
 *
 * <p>核心不变量：<b>确认盘点结果不得改动资产台账</b>。
 * 历史实现会在确认时把盘亏资产置为「闲置」、盘盈置为「在用」，
 * 而「闲置」在系统里意味着"可被领用"，等于让已丢失的资产重新进入流通；
 * 同时它会与在途审批的条件更新（{@code eq(status, 期望值)}）冲突导致审批卡死。
 */
@ExtendWith(MockitoExtension.class)
class CheckConfirmTest {

    private static final Long TASK_ID = 7L;

    @Mock private CheckTaskMapper checkTaskMapper;
    @Mock private CheckRecordMapper checkRecordMapper;
    @Mock private AssetMapper assetMapper;
    @Mock private LLMService llmService;
    @Mock private UserMapper userMapper;
    @Mock private DepartmentMapper departmentMapper;

    @InjectMocks private CheckServiceImpl checkService;

    private static CheckRecord record(Long id, Long taskId, String result) {
        CheckRecord r = new CheckRecord();
        r.setId(id);
        r.setTaskId(taskId);
        r.setAssetId(100L + id);
        r.setResult(result);
        return r;
    }

    @Test
    @DisplayName("确认盘亏/盘盈/相符：只统计，绝不写资产台账")
    void confirmDiff_mustNotTouchAssetLedger() {
        when(checkRecordMapper.selectById(11L)).thenReturn(record(11L, TASK_ID, "SHORTAGE"));
        when(checkRecordMapper.selectById(12L)).thenReturn(record(12L, TASK_ID, "SURPLUS"));
        when(checkRecordMapper.selectById(13L)).thenReturn(record(13L, TASK_ID, "MATCH"));
        CheckTask task = new CheckTask();
        task.setId(TASK_ID);
        task.setStatus("IN_PROGRESS");
        when(checkTaskMapper.selectById(TASK_ID)).thenReturn(task);

        Map<String, Object> result = checkService.confirmDiff(TASK_ID, List.of(11L, 12L, 13L));

        assertThat(result)
                .containsEntry("confirmed", 3)
                .containsEntry("shortage", 1)
                .containsEntry("surplus", 1)
                .containsEntry("matched", 1);

        // 这是本测试的重点：任何形式的资产写入都必须为零
        verify(assetMapper, never()).update(any(), any());
        verify(assetMapper, never()).updateById(any());
        verify(assetMapper, never()).insert(any());
    }

    @Test
    @DisplayName("确认后任务状态置为 COMPLETED")
    void confirmDiff_marksTaskCompleted() {
        when(checkRecordMapper.selectById(11L)).thenReturn(record(11L, TASK_ID, "MATCH"));
        CheckTask task = new CheckTask();
        task.setId(TASK_ID);
        task.setStatus("IN_PROGRESS");
        when(checkTaskMapper.selectById(TASK_ID)).thenReturn(task);

        checkService.confirmDiff(TASK_ID, List.of(11L));

        assertThat(task.getStatus()).isEqualTo("COMPLETED");
        verify(checkTaskMapper).updateById(task);
    }

    @Test
    @DisplayName("跨任务传入的 recordId 被跳过，不参与统计")
    void confirmDiff_skipsRecordsOfOtherTask() {
        when(checkRecordMapper.selectById(21L)).thenReturn(record(21L, TASK_ID, "SHORTAGE"));
        // 属于另一个任务（taskId=99）
        when(checkRecordMapper.selectById(22L)).thenReturn(record(22L, 99L, "SHORTAGE"));
        when(checkTaskMapper.selectById(TASK_ID)).thenReturn(new CheckTask());

        Map<String, Object> result = checkService.confirmDiff(TASK_ID, List.of(21L, 22L));

        assertThat(result).containsEntry("confirmed", 1).containsEntry("shortage", 1);
        verify(assetMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("空记录列表不抛异常，统计全为 0")
    void confirmDiff_withEmptyList_isSafe() {
        when(checkTaskMapper.selectById(TASK_ID)).thenReturn(new CheckTask());

        Map<String, Object> result = checkService.confirmDiff(TASK_ID, List.of());

        assertThat(result)
                .containsEntry("confirmed", 0)
                .containsEntry("shortage", 0)
                .containsEntry("surplus", 0)
                .containsEntry("matched", 0);
        verify(checkRecordMapper, never()).selectById(anyLong());
    }
}
