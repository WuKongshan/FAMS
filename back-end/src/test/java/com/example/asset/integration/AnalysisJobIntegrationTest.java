package com.example.asset.integration;

import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.check.dto.AnalysisJobVO;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;
import com.example.asset.modules.check.mapper.CheckRecordMapper;
import com.example.asset.modules.check.mapper.CheckTaskMapper;
import com.example.asset.modules.check.service.AnalysisJobService;
import com.example.asset.modules.check.service.CheckService;
import com.example.asset.modules.check.service.impl.AnalysisJobServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * 分析作业：真实线程池 + 真实落库的状态流转。
 *
 * <p>单测把线程池换成了同步执行，能验证逻辑分支，但验证不了
 * "提交立即返回、后台线程异步执行、跑完再落 SUCCESS"这条真实时序，
 * 也验证不了 {@code engine=rule / degraded=true} 这类降级标记是否真的写进了库。
 * 分析服务在测试中关闭（见基类），因此走的是"调用失败 → 模板降级"路径，不联网。</p>
 *
 * <p><b>为什么用 {@code @SpyBean} 卡住 analyzeDiff</b>：只断言 {@code submit()} 返回 PENDING 是
 * 空断言 —— {@code submit()} 返回的是内存实体 {@code job} 的 VO，它永远是 PENDING；
 * 即使把线程池执行改成同步 {@code run(...)}，这个断言依旧成立。必须让后台线程在
 * analyzeDiff 里停住，再从"另一个连接"观察 DB 状态，才能真正区分同步与异步。</p>
 *
 * <p><b>代价（刻意接受）</b>：{@code @SpyBean} 属于 Mockito ContextCustomizer，会改变上下文缓存键，
 * 使本类单独拥有一个 Spring 上下文（其余 4 个集成测试类共用另一个），冷启动多约 30s。
 * 不要为了"省时间"删掉它或把用例合并回空断言 —— 那样会失去对异步契约的证伪能力。</p>
 *
 * <p><b>不用 {@code @Transactional}</b>：异步线程看不到未提交的数据，状态更新也会另开连接。</p>
 */
@DisplayName("分析作业：异步状态流转 + 降级标记")
class AnalysisJobIntegrationTest extends AbstractIntegrationTest {

    private static final long POLL_TIMEOUT_SECONDS = 30;

    @Autowired
    private AnalysisJobService analysisJobService;
    @Autowired
    private CheckTaskMapper checkTaskMapper;
    @Autowired
    private CheckRecordMapper checkRecordMapper;
    @Autowired
    private AssetMapper assetMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private CheckService checkService;

    private Long taskId;
    private Long assetId;

    @AfterEach
    void cleanUp() {
        if (taskId != null) {
            jdbc.update("DELETE FROM analysis_job WHERE task_id = ?", taskId);
            jdbc.update("DELETE FROM check_record WHERE task_id = ?", taskId);
            jdbc.update("DELETE FROM check_task WHERE id = ?", taskId);
        }
        if (assetId != null) {
            jdbc.update("DELETE FROM asset_info WHERE id = ?", assetId);
        }
        taskId = null;
        assetId = null;
    }

    @Test
    @DisplayName("submit 立即返回且状态未完成：后台线程确实在另一线程里跑（同步实现会红）")
    void submit_returnsBeforeAnalysisCompletes() throws Exception {
        prepareTaskWithShortageRecord();

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            // 卡住后台线程，让主线程有机会观察"未完成"的中间态
            release.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(checkService).analyzeDiff(anyLong());

        AnalysisJobVO submitted;
        long elapsedMillis;
        try {
            long startNanos = System.nanoTime();
            submitted = analysisJobService.submit(taskId);
            elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            // 1) 后台线程真的进来了（同步实现也会进来，单看这条区分不了，作为后续断言的前提）
            assertThat(entered.await(5, TimeUnit.SECONDS))
                    .as("线程池应在 submit 返回后 5 秒内开始执行分析")
                    .isTrue();

            // 2) 粗上限（防呆，不是主判据）：doAnswer 固定阻塞 10s，同步化必然多等约 10s，
            //    所以任何 <10s 的阈值都能命中；这里放宽到 5s 只为避开满载 CI 的抖动。
            //    保留它是因为报错更直观（"submit 耗时 XXXXms"）；真正的判据是下面第 3 条。
            assertThat(elapsedMillis)
                    .as("submit 的粗上限（防呆，非主判据）：耗时 %dms，接近阻塞时长说明线程池可能退化为同步执行", elapsedMillis)
                    .isLessThan(5000);

            assertThat(submitted.getJobId()).isNotNull();
            assertThat(submitted.getStatus()).isEqualTo(AnalysisJobServiceImpl.STATUS_PENDING);
            assertThat(submitted.isFinished()).isFalse();

            // 3) 关键：另开连接查库。同步实现此刻已经跑完并写入 SUCCESS，这里会红。
            String statusInDb = jdbc.queryForObject(
                    "SELECT status FROM analysis_job WHERE id = ?", String.class, submitted.getJobId());
            assertThat(statusInDb)
                    .as("分析被卡住期间，作业只能处于 PENDING/RUNNING，绝不能已经 SUCCESS")
                    .isIn(AnalysisJobServiceImpl.STATUS_PENDING, AnalysisJobServiceImpl.STATUS_RUNNING)
                    .isNotEqualTo(AnalysisJobServiceImpl.STATUS_SUCCESS);
        } finally {
            // 无论如何都要放行，否则后台线程会卡满 10 秒，拖慢构建
            release.countDown();
        }

        AnalysisJobVO finished = pollUntilFinished(submitted.getJobId());
        assertThat(finished.getStatus()).isEqualTo(AnalysisJobServiceImpl.STATUS_SUCCESS);
        assertThat(finished.getEngine()).isEqualTo("rule");
        assertThat(finished.isDegraded()).isTrue();
    }

    @Test
    @DisplayName("后台分析最终 SUCCESS：engine=rule、degraded=true、result 非空且已落库")
    void asyncJob_finishesAsSuccessWithRuleEngine() throws Exception {
        prepareTaskWithShortageRecord();

        AnalysisJobVO submitted = analysisJobService.submit(taskId);
        AnalysisJobVO finished = pollUntilFinished(submitted.getJobId());

        assertThat(finished.getStatus()).isEqualTo(AnalysisJobServiceImpl.STATUS_SUCCESS);
        assertThat(finished.getEngine()).isEqualTo("rule");
        assertThat(finished.isDegraded())
                .as("分析服务不可用时必须显式标记为降级，否则前端会把模板文案当成 AI 结论")
                .isTrue();
        assertThat(finished.isFinished()).isTrue();
        assertThat(finished.getResult()).isNotNull().hasSize(1);
        assertThat(finished.getResult().get(0).getRecordId()).isNotNull();

        // 落库校验：不只是 VO 上算出来的，DB 里确实存了 engine 与 result_json
        String engine = jdbc.queryForObject(
                "SELECT engine FROM analysis_job WHERE id = ?", String.class, submitted.getJobId());
        String resultJson = jdbc.queryForObject(
                "SELECT result_json FROM analysis_job WHERE id = ?", String.class, submitted.getJobId());
        assertThat(engine).isEqualTo("rule");
        assertThat(resultJson).isNotBlank();
    }

    private AnalysisJobVO pollUntilFinished(Long jobId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(POLL_TIMEOUT_SECONDS);
        AnalysisJobVO vo = null;
        while (System.nanoTime() < deadline) {
            vo = analysisJobService.getStatus(jobId);
            if (vo.isFinished()) {
                return vo;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("分析作业在 " + POLL_TIMEOUT_SECONDS + " 秒内未结束，最后状态: "
                + (vo == null ? "null" : vo.getStatus()));
    }

    private void prepareTaskWithShortageRecord() {
        CheckTask task = new CheckTask();
        task.setTaskNo("IT-ANALYSIS-" + UUID.randomUUID());
        task.setTaskName("分析作业测试任务");
        task.setStatus("IN_PROGRESS");
        checkTaskMapper.insert(task);
        taskId = task.getId();

        Asset asset = new Asset();
        // asset_code 列宽 50，前缀 + UUID(36) 不能超长
        asset.setAssetCode("IT-AN-" + UUID.randomUUID());
        asset.setAssetName("分析作业测试资产");
        asset.setQuantity(1);
        asset.setStatus("在用");
        assetMapper.insert(asset);
        assetId = asset.getId();

        CheckRecord record = new CheckRecord();
        record.setTaskId(taskId);
        record.setAssetId(assetId);
        record.setAssetCode(asset.getAssetCode());
        record.setAssetName(asset.getAssetName());
        record.setBookQuantity(1);
        record.setActualQuantity(0);
        record.setDiffQuantity(-1);
        record.setResult("SHORTAGE");
        record.setDiffHandleStatus("PENDING");
        checkRecordMapper.insert(record);
    }
}
