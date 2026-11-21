-- ============================================
-- Flyway V8: 报废申请单接入 Flowable 工作流
-- ============================================
-- 设计同 V7（apply_order）：
--   1. 该列可空。为空即表示该报废单未接入工作流，审批时自动回落到原有的硬编码
--      状态机分支，因此存量数据无需回填，新老逻辑可长期共存。
--   2. 一旦非空，即表示该报废单由 Flowable 流程实例驱动。
--   3. Flowable 自身的 ACT_* 表由引擎通过 flowable.database-schema-update=true 维护，
--      不在此迁移脚本中处理。
--
-- 说明：报废与领用共用同一个 BPMN 流程定义（assetApplyApproval），
-- 因为两者的两级审批流转规则完全一致（院系初审 → 校级复审）。

ALTER TABLE scrap_order
    ADD COLUMN process_instance_id VARCHAR(64) NULL COMMENT 'Flowable 流程实例ID（NULL=未接入工作流）' AFTER status;

-- 按流程实例反查报废单
CREATE INDEX idx_scrap_order_process_instance ON scrap_order (process_instance_id);
