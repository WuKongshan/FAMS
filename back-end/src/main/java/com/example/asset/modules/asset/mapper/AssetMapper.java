package com.example.asset.modules.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.asset.modules.asset.entity.Asset;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AssetMapper extends BaseMapper<Asset> {

    /**
     * 清理「脏占位」：若 {@code pending_order_id} 指向的单据已不在途（已终结 / 已逻辑删除 / 不存在），
     * 则把占位置空。返回受影响行数，0 表示占位仍然有效（持有者确实还在审批中）。
     *
     * <p><b>为什么用原生 SQL</b>：判断持有者是否在途要同时看 {@code apply_order} 与 {@code scrap_order}，
     * 而 MyBatis-Plus 的 Wrapper 无法表达 {@code NOT EXISTS} 子查询；
     * 放在 SQL 里同时避免了 asset 模块去 import approval/scrap 的实体与 Mapper（模块环依赖）。</p>
     *
     * <p><b>为什么是"先清理再重试"而不是"先查后判"</b>：这条 UPDATE 自带
     * {@code NOT EXISTS} 前置条件，判断与写入在一条语句内完成，不存在读后写的窗口。</p>
     *
     * <p>注意：这里的 {@code PENDING_FIRST / PENDING_SECOND} 是「在途」的唯一判据，
     * 与前端状态契约一致；新增状态时须同步修改本语句。</p>
     */
    @Update("UPDATE asset_info SET pending_order_id = NULL "
          + "WHERE id = #{assetId} AND deleted = 0 AND pending_order_id IS NOT NULL "
          + "  AND NOT EXISTS (SELECT 1 FROM apply_order o WHERE o.id = asset_info.pending_order_id "
          + "                  AND o.deleted = 0 AND o.status IN ('PENDING_FIRST','PENDING_SECOND')) "
          + "  AND NOT EXISTS (SELECT 1 FROM scrap_order s WHERE s.id = asset_info.pending_order_id "
          + "                  AND s.deleted = 0 AND s.status IN ('PENDING_FIRST','PENDING_SECOND'))")
    int clearStaleClaim(@Param("assetId") Long assetId);
}
