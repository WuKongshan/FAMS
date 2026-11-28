package com.example.asset.integration;

import com.example.asset.common.exception.BusinessException;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;
import com.example.asset.modules.check.mapper.CheckRecordMapper;
import com.example.asset.modules.check.mapper.CheckTaskMapper;
import com.example.asset.modules.check.service.CheckService;
import com.example.asset.modules.check.service.impl.CheckServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 差异处置闭环在真实数据上的行为。
 *
 * <p>两条核心不变量：</p>
 * <ol>
 *   <li>"待处置"列表只认 {@code diff_handle_status = 'PENDING'}，相符记录（NULL）不参与；</li>
 *   <li>处置只是打标签，<b>资产台账一行都不能被改</b> —— 资产状态只能由报废/新增审批流变更。</li>
 * </ol>
 */
@Transactional
@DisplayName("盘点差异处置：真实数据验证")
class CheckDiffHandleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CheckService checkService;
    @Autowired
    private CheckRecordMapper checkRecordMapper;
    @Autowired
    private CheckTaskMapper checkTaskMapper;
    @Autowired
    private AssetMapper assetMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private Long taskId;
    private Long otherTaskId;
    private Long assetId;
    private Long shortageRecordId;
    private Long matchRecordId;
    private Long otherTaskRecordId;
    private Long handledRecordId;

    private static final LocalDateTime FIRST_HANDLE_TIME = LocalDateTime.of(2020, 1, 1, 0, 0, 5);

    @BeforeEach
    void setUp() {
        taskId = insertTask("IT-盘点任务");
        otherTaskId = insertTask("IT-盘点任务-另一个");

        assetId = insertAsset();
        Long matchAssetId = insertAsset();

        shortageRecordId = insertRecord(taskId, assetId, "SHORTAGE", "PENDING", -1);
        matchRecordId = insertRecord(taskId, matchAssetId, "MATCH", null, 0);
        otherTaskRecordId = insertRecord(otherTaskId, assetId, "SHORTAGE", "PENDING", -2);

        // 一条已经处置过的记录，用于幂等校验（时间取固定历史值，避免秒级精度导致误判）
        handledRecordId = insertRecord(taskId, insertAsset(), "SHORTAGE", "HANDLED", -3);
        jdbc.update("UPDATE check_record SET diff_handle_type = 'SCRAP', diff_handle_time = ? WHERE id = ?",
                FIRST_HANDLE_TIME, handledRecordId);
    }

    @Test
    @DisplayName("待处置列表：只返回本任务下 PENDING 的差异记录，排除相符与其他任务")
    void listPendingDiffs_returnsOnlyPendingDiffOfTask() {
        List<CheckRecord> pending = checkService.listPendingDiffs(taskId);

        assertThat(pending)
                .extracting(CheckRecord::getId)
                .containsExactly(shortageRecordId)
                .doesNotContain(matchRecordId, otherTaskRecordId, handledRecordId);
        assertThat(pending.get(0).getResult()).isEqualTo("SHORTAGE");
        assertThat(pending.get(0).getDiffHandleStatus()).isEqualTo(CheckServiceImpl.DIFF_PENDING);
    }

    @Test
    @DisplayName("处置为 SCRAP：状态/方式/时间落库，资产台账完全未变")
    void handleDiff_marksHandledWithoutTouchingAsset() {
        Map<String, Object> assetBefore = jdbc.queryForMap(
                "SELECT * FROM asset_info WHERE id = ?", assetId);

        checkService.handleDiff(shortageRecordId, "SCRAP", null);

        CheckRecord record = checkRecordMapper.selectById(shortageRecordId);
        assertThat(record.getDiffHandleStatus()).isEqualTo(CheckServiceImpl.DIFF_HANDLED);
        assertThat(record.getDiffHandleType()).isEqualTo("SCRAP");
        assertThat(record.getDiffHandleTime()).isNotNull();
        assertThat(record.getResult()).isEqualTo("SHORTAGE");   // 原始盘点结论不被覆盖

        Map<String, Object> assetAfter = jdbc.queryForMap(
                "SELECT * FROM asset_info WHERE id = ?", assetId);
        assertThat(assetAfter)
                .as("处置差异不得修改资产任何字段（状态/使用人/更新时间）")
                .isEqualTo(assetBefore);
        assertThat(assetMapper.selectById(assetId).getStatus()).isEqualTo("在用");
    }

    @Test
    @DisplayName("重复处置幂等：不报错，且不覆盖首次的处置方式与时间")
    void handleDiff_isIdempotent() {
        checkService.handleDiff(handledRecordId, "MANUAL", null);

        CheckRecord record = checkRecordMapper.selectById(handledRecordId);
        assertThat(record.getDiffHandleStatus()).isEqualTo(CheckServiceImpl.DIFF_HANDLED);
        assertThat(record.getDiffHandleType())
                .as("重复请求不得把 SCRAP 改成 MANUAL")
                .isEqualTo("SCRAP");
        assertThat(record.getDiffHandleTime())
                .as("重复请求不得刷新首次处置时间")
                .isEqualTo(FIRST_HANDLE_TIME);
    }

    @Test
    @DisplayName("处置写入关联单号：真实列落库，前后空白被清掉")
    void handleDiff_persistsRefNo() {
        checkService.handleDiff(shortageRecordId, "SCRAP", "  废2026-0031  ");

        CheckRecord record = checkRecordMapper.selectById(shortageRecordId);
        assertThat(record.getDiffHandleRef()).isEqualTo("废2026-0031");

        String raw = jdbc.queryForObject(
                "SELECT diff_handle_ref FROM check_record WHERE id = ?", String.class, shortageRecordId);
        assertThat(raw).as("库里的值不该带空白").isEqualTo("废2026-0031");
    }

    @Test
    @DisplayName("撤销处置：五个处置字段在真实库里全部被清空")
    void revokeDiff_clearsAllHandleFieldsInDb() {
        checkService.handleDiff(shortageRecordId, "SCRAP", "废2026-0031");
        assertThat(checkRecordMapper.selectById(shortageRecordId).getDiffHandleStatus())
                .isEqualTo(CheckServiceImpl.DIFF_HANDLED);

        checkService.revokeDiff(shortageRecordId);

        // 直接查库而不是查实体：目的是验证 SET 子句真的把列写成了 NULL。
        // 若用实体更新（NOT_NULL 策略），null 字段不会进 SET，这里会残留旧值 —— 正是本用例要抓的。
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT diff_handle_status, diff_handle_type, diff_handle_ref, diff_handle_time, diff_handle_by "
                        + "FROM check_record WHERE id = ?", shortageRecordId);

        assertThat(row.get("diff_handle_status")).isEqualTo(CheckServiceImpl.DIFF_PENDING);
        assertThat(row.get("diff_handle_type")).as("撤销后不得残留处置方式").isNull();
        assertThat(row.get("diff_handle_ref")).as("撤销后不得残留关联单号").isNull();
        assertThat(row.get("diff_handle_time")).as("撤销后不得残留处置时间").isNull();
        assertThat(row.get("diff_handle_by")).as("撤销后不得残留处置人").isNull();

        // 撤销后必须重新出现在待处置列表里，否则差异还是"消失"状态
        assertThat(checkService.listPendingDiffs(taskId))
                .extracting(CheckRecord::getId)
                .contains(shortageRecordId);
        assertThat(checkService.listHandledDiffs(taskId))
                .extracting(CheckRecord::getId)
                .doesNotContain(shortageRecordId);
    }

    @Test
    @DisplayName("已处置列表：撤销后从列表消失，处置时出现在列表里")
    void listHandledDiffs_tracksRevoke() {
        checkService.handleDiff(shortageRecordId, "MANUAL", null);
        assertThat(checkService.listHandledDiffs(taskId))
                .extracting(CheckRecord::getId)
                .contains(shortageRecordId);

        checkService.revokeDiff(shortageRecordId);
        assertThat(checkService.listHandledDiffs(taskId))
                .extracting(CheckRecord::getId)
                .doesNotContain(shortageRecordId);
    }

    @Test
    @DisplayName("非法处置方式：抛 BusinessException 且不落库")
    void handleDiff_rejectsUnknownType() {        assertThatThrownBy(() -> checkService.handleDiff(shortageRecordId, "DELETE_ASSET", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("处置方式不合法");

        CheckRecord record = checkRecordMapper.selectById(shortageRecordId);
        assertThat(record.getDiffHandleStatus()).isEqualTo(CheckServiceImpl.DIFF_PENDING);
        assertThat(record.getDiffHandleType()).isNull();
    }

    @Test
    @DisplayName("相符记录不接受处置：抛 BusinessException 且不落库")
    void handleDiff_rejectsMatchRecord() {
        assertThatThrownBy(() -> checkService.handleDiff(matchRecordId, "MANUAL", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账实相符");

        assertThat(checkRecordMapper.selectById(matchRecordId).getDiffHandleStatus()).isNull();
    }

    private Long insertTask(String name) {
        CheckTask task = new CheckTask();
        task.setTaskNo("IT-" + UUID.randomUUID());
        task.setTaskName(name);
        task.setStatus("IN_PROGRESS");
        checkTaskMapper.insert(task);
        return task.getId();
    }

    private Long insertAsset() {
        Asset asset = new Asset();
        asset.setAssetCode("IT-DIFF-" + UUID.randomUUID());
        asset.setAssetName("差异处置测试资产");
        asset.setQuantity(1);
        asset.setStatus("在用");
        assetMapper.insert(asset);
        return asset.getId();
    }

    private Long insertRecord(Long taskId, Long assetId, String result,
                              String diffHandleStatus, int diffQuantity) {
        CheckRecord record = new CheckRecord();
        record.setTaskId(taskId);
        record.setAssetId(assetId);
        record.setAssetCode("IT-ASSET-" + assetId);
        record.setAssetName("差异处置测试资产");
        record.setBookQuantity(1);
        record.setActualQuantity(1 + diffQuantity);
        record.setDiffQuantity(diffQuantity);
        record.setResult(result);
        record.setDiffHandleStatus(diffHandleStatus);
        checkRecordMapper.insert(record);
        return record.getId();
    }
}
