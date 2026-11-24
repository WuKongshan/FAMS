-- ============================================
-- Flyway V9: 资产「在途申请」占位字段
-- ============================================
-- 背景：同一资产可能被并发或连续提交多张进行中的申请单（领用 / 归还 / 报废）。
-- 原先只靠「资产当前状态」校验，但状态要到终审才变化，
-- 整个审批窗口内资产一直处于「闲置」，因此任意时刻都能再提交一张，产生多张 pending 单。
--
-- 设计：
--   1. pending_order_id 非空 = 该资产已有进行中的申请单，拒绝新的申请。
--   2. 占位与释放都用「条件更新」完成（WHERE ... AND pending_order_id IS NULL），
--      不做"先查后写"，天然并发安全。
--   3. 领用/归还存于 apply_order、报废存于 scrap_order，但占位字段统一落在 asset_info 这一行上，
--      因此天然实现「跨单据类型互斥」。
--      （MySQL 唯一索引无法跨表，这是不采用「在单据表加 pending_asset_id + 唯一索引」的原因。）
--   4. 释放时必须带 pending_order_id = 单据ID 的条件，
--      避免 A 单的延迟释放把 B 单刚占上的位置清掉。
--   5. 该列不参与前端状态契约，asset_info.status 的语义完全不变。
--
-- 兼容性：可空、无默认值，存量数据全为 NULL（= 无在途申请），无需回填。

ALTER TABLE asset_info
    ADD COLUMN pending_order_id BIGINT NULL
        COMMENT '进行中的申请单ID（领用/归还/报废共用，NULL=无在途申请）' AFTER status;

CREATE INDEX idx_asset_pending_order ON asset_info (pending_order_id);
