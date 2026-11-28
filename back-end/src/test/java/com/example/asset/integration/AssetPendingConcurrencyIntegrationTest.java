package com.example.asset.integration;

import com.example.asset.modules.approval.entity.ApplyOrder;
import com.example.asset.modules.approval.mapper.ApplyOrderMapper;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.asset.service.AssetPendingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资产占位的并发原子性。
 *
 * <p>这是历史事故"同一资产被重复申请"的根因校验：占位靠一条
 * {@code UPDATE ... WHERE pending_order_id IS NULL} 完成，只有真实 MySQL 的行锁能证明
 * 并发下恰好一个成功。Mockito 里 {@code when(...).thenReturn(1)} 永远证明不了这件事。</p>
 *
 * <p><b>不用 {@code @Transactional}</b>：事务里造的数据其他线程看不见，
 * 两个线程也会各自开事务，测不出真实并发。数据由本类在 {@code @AfterEach} 物理删除。</p>
 */
@DisplayName("资产占位：真实 MySQL 并发原子性")
class AssetPendingConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AssetPendingService assetPendingService;
    @Autowired
    private AssetMapper assetMapper;
    @Autowired
    private ApplyOrderMapper applyOrderMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdAssetIds = new ArrayList<>();
    private final List<Long> createdOrderIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        if (!createdOrderIds.isEmpty()) {
            jdbc.update("DELETE FROM apply_order WHERE id IN ("
                    + placeholders(createdOrderIds.size()) + ")", createdOrderIds.toArray());
        }
        if (!createdAssetIds.isEmpty()) {
            jdbc.update("DELETE FROM asset_info WHERE id IN ("
                    + placeholders(createdAssetIds.size()) + ")", createdAssetIds.toArray());
        }
        createdOrderIds.clear();
        createdAssetIds.clear();
    }

    @Test
    @DisplayName("两个线程同时 claim 同一资产：恰好一个成功，pending_order_id 只被赢家写入")
    void concurrentClaim_onlyOneWins() throws Exception {
        Long assetId = insertAsset();
        // 两单都真实处于在途状态：否则失败方会走"脏占位自愈"（NOT EXISTS 命中）把赢家的占位清掉，
        // 这是正常设计，不属于本用例要覆盖的场景。
        Long order1 = insertInFlightOrder(assetId, 900101L);
        Long order2 = insertInFlightOrder(assetId, 900102L);

        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<ClaimResult> f1 = pool.submit(() -> claimAfterGate(assetId, order1, bothReady, startGate));
            Future<ClaimResult> f2 = pool.submit(() -> claimAfterGate(assetId, order2, bothReady, startGate));

            assertThat(bothReady.await(10, TimeUnit.SECONDS))
                    .as("两个线程都应在 10 秒内就绪")
                    .isTrue();
            startGate.countDown();

            ClaimResult r1 = f1.get(20, TimeUnit.SECONDS);
            ClaimResult r2 = f2.get(20, TimeUnit.SECONDS);

            List<ClaimResult> results = List.of(r1, r2);
            long successCount = results.stream().filter(ClaimResult::claimed).count();
            assertThat(successCount)
                    .as("同一资产并发占用必须恰好一个成功，实际结果: %s", results)
                    .isEqualTo(1);

            ClaimResult winner = results.stream()
                    .filter(ClaimResult::claimed)
                    .findFirst()
                    .orElseThrow();

            Asset after = assetMapper.selectById(assetId);
            assertThat(after.getPendingOrderId())
                    .as("占位必须等于赢家单据ID，说明只有赢家写入过")
                    .isEqualTo(winner.orderId());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("释放占位必须匹配单据ID：错误单据不清占位，正确单据才清")
    void release_onlyClearsMatchingOrder() {
        Long assetId = insertAsset();
        Long holder = insertInFlightOrder(assetId, 900103L);
        Long other = insertInFlightOrder(assetId, 900104L);

        assertThat(assetPendingService.claim(assetId, holder)).isTrue();

        assetPendingService.release(assetId, other);
        assertThat(assetMapper.selectById(assetId).getPendingOrderId())
                .as("A 单的延迟释放不得清掉 B 单的占位")
                .isEqualTo(holder);

        assetPendingService.release(assetId, holder);
        assertThat(assetMapper.selectById(assetId).getPendingOrderId())
                .as("持有者释放后资产应重新可申请")
                .isNull();
    }

    private ClaimResult claimAfterGate(Long assetId, Long orderId,
                                       CountDownLatch ready, CountDownLatch gate) throws InterruptedException {
        ready.countDown();
        gate.await(10, TimeUnit.SECONDS);
        return new ClaimResult(orderId, assetPendingService.claim(assetId, orderId));
    }

    private Long insertAsset() {
        Asset asset = new Asset();
        asset.setAssetCode("IT-CONC-" + UUID.randomUUID());
        asset.setAssetName("并发测试资产");
        asset.setQuantity(1);
        asset.setStatus("闲置");
        assetMapper.insert(asset);
        createdAssetIds.add(asset.getId());
        return asset.getId();
    }

    private Long insertInFlightOrder(Long assetId, long applyUserId) {
        ApplyOrder order = new ApplyOrder();
        order.setApplyType("BORROW");
        order.setAssetId(assetId);
        order.setApplyUserId(applyUserId);
        order.setApplyUserName("并发测试用户");
        order.setStatus("PENDING_FIRST");
        applyOrderMapper.insert(order);
        createdOrderIds.add(order.getId());
        return order.getId();
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private record ClaimResult(Long orderId, boolean claimed) {
    }
}
