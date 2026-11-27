package com.example.asset.modules.check.service.impl;

import com.example.asset.modules.admin.mapper.DepartmentMapper;
import com.example.asset.modules.admin.mapper.UserMapper;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.check.client.CheckAnalysisClient;
import com.example.asset.modules.check.dto.AnalysisResult;
import com.example.asset.modules.check.dto.CheckDiffResult;
import com.example.asset.modules.check.dto.LLMAnalysisItem;
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
import static org.mockito.Mockito.when;

/**
 * 差异分析的降级测试。
 *
 * <p>核心不变量：<b>分析服务挂了也要有结果</b>。
 * Java 侧不再懂 LLM，只负责发一次请求；服务不可用时必须退回模板文案，
 * 不能把异常抛给前端，也不能让某条记录的分析为空。
 */
@ExtendWith(MockitoExtension.class)
class CheckAnalysisFallbackTest {

    private static final Long TASK_ID = 7L;

    @Mock private CheckTaskMapper checkTaskMapper;
    @Mock private CheckRecordMapper checkRecordMapper;
    @Mock private AssetMapper assetMapper;
    @Mock private CheckAnalysisClient analysisClient;
    @Mock private UserMapper userMapper;
    @Mock private DepartmentMapper departmentMapper;

    @InjectMocks private CheckServiceImpl checkService;

    private static CheckRecord record(Long id, int bookQuantity, int actualQuantity, String result) {
        CheckRecord r = new CheckRecord();
        r.setId(id);
        r.setTaskId(TASK_ID);
        r.setAssetName("测试资产" + id);
        r.setAssetCode("CODE-" + id);
        r.setBookQuantity(bookQuantity);
        r.setActualQuantity(actualQuantity);
        r.setDiffQuantity(actualQuantity - bookQuantity);
        r.setResult(result);
        return r;
    }

    @Test
    @DisplayName("分析服务抛异常：回退模板，每条记录都有分析且不带异常")
    void analyzeDiff_whenClientFails_fallsBackToTemplate() {
        when(checkRecordMapper.selectList(any())).thenReturn(List.of(
                record(11L, 5, 4, "SHORTAGE"),
                record(12L, 2, 4, "SURPLUS"),
                record(13L, 3, 3, "MATCH")));
        when(checkTaskMapper.selectById(TASK_ID)).thenReturn(new CheckTask());
        when(analysisClient.analyze(any(), any())).thenThrow(new RuntimeException("boom"));

        AnalysisResult result = checkService.analyzeDiff(TASK_ID);
        List<CheckDiffResult> results = result.getItems();

        assertThat(results).hasSize(3);
        assertThat(result.getEngine()).isEqualTo("rule");  // 降级必须如实标记来源
        assertThat(results).allSatisfy(r -> {
            assertThat(r.getAnalysis()).isNotBlank();
            assertThat(r.getSuggestion()).isNotBlank();
        });
        // 模板文案：差异记录是"系统检测到资产…"，相符记录是"账面与实物相符"
        assertThat(results).allSatisfy(r ->
                assertThat(r.getAnalysis()).containsAnyOf("系统检测到资产", "账面与实物相符"));
    }

    @Test
    @DisplayName("分析服务正常：使用服务返回的文案")
    void analyzeDiff_whenClientSucceeds_usesServiceText() {
        when(checkRecordMapper.selectList(any())).thenReturn(List.of(record(11L, 5, 4, "SHORTAGE")));
        when(checkTaskMapper.selectById(TASK_ID)).thenReturn(new CheckTask());

        LLMAnalysisItem item = new LLMAnalysisItem();
        item.setRecordId(11L);
        item.setAnalysis("模型分析X");
        item.setSuggestion("模型建议Y");
        when(analysisClient.analyze(any(), any()))
                .thenReturn(new CheckAnalysisClient.AnalysisOutcome(Map.of(11L, item), "llm"));

        AnalysisResult result = checkService.analyzeDiff(TASK_ID);
        List<CheckDiffResult> results = result.getItems();

        assertThat(results).hasSize(1);
        assertThat(result.getEngine()).isEqualTo("llm");
        assertThat(results.get(0).getRecordId()).isEqualTo(11L);
        assertThat(results.get(0).getAnalysis()).isEqualTo("模型分析X");
        assertThat(results.get(0).getSuggestion()).isEqualTo("模型建议Y");
    }
}
