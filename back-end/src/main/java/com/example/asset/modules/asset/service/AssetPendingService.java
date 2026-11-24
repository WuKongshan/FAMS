package com.example.asset.modules.asset.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 资产「在途申请」占位服务。
 *
 * <p><b>要解决的问题</b>：资产状态要到<b>终审</b>才变化，整个审批窗口内它仍是原状态。
 * 所以「读状态 → 判断可申请 → 插入单据」这类校验挡不住重复申请 ——
 * MySQL 默认 REPEATABLE READ 下普通 SELECT 是快照读、不加锁，两个请求都会读到「可申请」。</p>
 *
 * <p><b>做法</b>：用 {@code asset_info.pending_order_id} 做占位，
 * 占位与释放都通过<b>条件更新</b>完成，把并发控制交给 DB 的 WHERE 条件，不做"先查后写"。</p>
 *
 * <p><b>为什么占位落在 asset 表而不是单据表</b>：领用/归还与报废分属两张表，
 * MySQL 唯一索引无法跨表，只有落在共享的资产行上才能实现跨单据类型互斥。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetPendingService {

    private final AssetMapper assetMapper;

    /**
     * 尝试占用资产。
     *
     * <p>先做一次纯条件更新；失败时再判断是不是「脏占位」——即占位指向的单据
     * 早已终结/删除却没有释放。脏占位若不处理，该资产会被<b>永久锁死</b>
     * （用户再也提交不了，且查不出原因），这比重复申请更严重，所以必须能自愈。</p>
     *
     * <p>自愈方式：{@link AssetMapper#clearStaleClaim} 用一条带 NOT EXISTS 的 UPDATE
     * 原子地清掉失效占位，然后重试一次占用。清理未命中说明持有者确实还在审批中，正常拒绝。</p>
     *
     * @param assetId 资产ID
     * @param orderId 本次申请单ID（调用方须先 insert 拿到自增主键）
     * @return true 占用成功；false 该资产已被其它在途单据占用
     */
    public boolean claim(Long assetId, Long orderId) {
        if (doClaim(assetId, orderId)) {
            return true;
        }

        int cleared = assetMapper.clearStaleClaim(assetId);
        if (cleared == 0) {
            return false;   // 持有者仍在途，正常拒绝
        }

        log.warn("检测到脏占位并已清理, assetId={}, 新单据={}, 重试占用", assetId, orderId);
        return doClaim(assetId, orderId);
    }

    /**
     * 纯条件更新：只有当前 {@code pending_order_id IS NULL} 时才会写入成功。
     * 并发下由数据库行锁保证只有一个请求能把 NULL 改成非 NULL，其余返回 0。
     */
    private boolean doClaim(Long assetId, Long orderId) {
        int rows = assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
                .eq(Asset::getId, assetId)
                .isNull(Asset::getPendingOrderId)
                .set(Asset::getPendingOrderId, orderId));
        return rows > 0;
    }

    /**
     * 释放占用：单据进入终态（通过 / 驳回）后调用，把资产重新变为可申请。
     *
     * <p><b>必须带 {@code pending_order_id = orderId} 条件</b>：否则 A 单的延迟释放会清掉
     * B 单刚占上的位置，占位机制直接失效。</p>
     *
     * <p>未命中只告警、不抛异常。原因：占位可能已被
     * {@link AssetMapper#clearStaleClaim} 清理，或资产已被删除 ——
     * 这属于可接受的竞态，不应该因为"占位已经不在"就让一次合法的审批失败。</p>
     */
    public void release(Long assetId, Long orderId) {
        int rows = assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
                .eq(Asset::getId, assetId)
                .eq(Asset::getPendingOrderId, orderId)
                .set(Asset::getPendingOrderId, null));
        if (rows == 0) {
            log.warn("释放资产占位未命中（可能已被清理或资产已删除）: assetId={}, orderId={}", assetId, orderId);
        }
    }
}
