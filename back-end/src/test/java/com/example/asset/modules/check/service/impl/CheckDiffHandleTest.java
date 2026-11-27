package com.example.asset.modules.check.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.modules.admin.mapper.DepartmentMapper;
import com.example.asset.modules.admin.mapper.UserMapper;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.check.client.CheckAnalysisClient;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.mapper.CheckRecordMapper;
import com.example.asset.modules.check.mapper.CheckTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 差异处置闭环。
 *
 * <p>核心约束：处置只是打标签，<b>不碰资产台账</b> —— 资产状态变更仍必须走报废/新增审批，
 * 否则又回到"盘点顺手改台账"的老问题（盘亏置闲置 = 丢失资产重新可领用）。
 */
@ExtendWith(MockitoExtension.class)
class CheckDiffHandleTest {

    @Mock private CheckTaskMapper checkTaskMapper;
    @Mock private CheckRecordMapper checkRecordMapper;
    @Mock private AssetMapper assetMapper;
    @Mock private CheckAnalysisClient analysisClient;
    @Mock private UserMapper userMapper;
    @Mock private DepartmentMapper departmentMapper;

    @InjectMocks private CheckServiceImpl checkService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CheckRecord.class);
    }

    private static CheckRecord record(Long id, String result) {
        CheckRecord r = new CheckRecord();
        r.setId(id);
        r.setTaskId(7L);
        r.setAssetId(100L);
        r.setResult(result);
        r.setDiffHandleStatus("SHORTAGE".equals(result) ? CheckServiceImpl.DIFF_PENDING : null);
        return r;
    }

    @Test
    @DisplayName("处置差异：写入 HANDLED + 方式 + 时间，但不修改资产")
    void handleDiff_marksHandledWithoutTouchingAsset() {
        when(checkRecordMapper.selectById(22L)).thenReturn(record(22L, "SHORTAGE"));

        checkService.handleDiff(22L, "SCRAP");

        ArgumentCaptor<CheckRecord> captor = ArgumentCaptor.forClass(CheckRecord.class);
        verify(checkRecordMapper).updateById(captor.capture());
        CheckRecord update = captor.getValue();

        assertThat(update.getId()).isEqualTo(22L);
        assertThat(update.getDiffHandleStatus()).isEqualTo(CheckServiceImpl.DIFF_HANDLED);
        assertThat(update.getDiffHandleType()).isEqualTo("SCRAP");
        assertThat(update.getDiffHandleTime()).isNotNull();

        // 最重要的一条：处置不得动资产，资产状态只能由审批流改
        verify(assetMapper, never()).updateById(any());
        verify(assetMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("非法处置方式：拒绝，且不落库")
    void handleDiff_rejectsUnknownType() {
        assertThatThrownBy(() -> checkService.handleDiff(22L, "DELETE_ASSET"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("处置方式不合法");

        verify(checkRecordMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("相符记录不接受处置：直接拒绝")
    void handleDiff_rejectsMatchRecord() {
        when(checkRecordMapper.selectById(30L)).thenReturn(record(30L, "MATCH"));

        assertThatThrownBy(() -> checkService.handleDiff(30L, "MANUAL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账实相符");

        verify(checkRecordMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("重复处置幂等：不重复写库，不覆盖首次的处置人")
    void handleDiff_isIdempotent() {
        CheckRecord handled = record(22L, "SHORTAGE");
        handled.setDiffHandleStatus(CheckServiceImpl.DIFF_HANDLED);
        when(checkRecordMapper.selectById(22L)).thenReturn(handled);

        checkService.handleDiff(22L, "MANUAL");

        verify(checkRecordMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("待处置列表：只查 PENDING，支持按任务过滤")
    void listPendingDiffs_queriesPendingOnly() {
        when(checkRecordMapper.selectList(any())).thenReturn(List.of(record(22L, "SHORTAGE"), record(23L, "SURPLUS")));

        List<CheckRecord> pending = checkService.listPendingDiffs(7L);

        assertThat(pending).hasSize(2);
        verify(checkRecordMapper).selectList(any());
    }
}
