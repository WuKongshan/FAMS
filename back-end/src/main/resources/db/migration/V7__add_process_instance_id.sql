-- ============================================
-- Flyway V7: 领用/归还申请单接入 Flowable 工作流
-- ============================================
-- 为 apply_order 增加 Flowable 流程实例ID。
-- 设计要点：
--   1. 该列为可空。历史数据、以及未接入 Flowable 的申请类型（如归还）保持 NULL，
--      审批时自动走原有的硬编码状态机分支，实现平滑过渡、无需数据回填。
--   2. 一旦非空，即表示该申请单由 Flowable 流程实例驱动，审批走 BPMN 分支。
--   3. Flowable 自身的 ACT_* 表由 engine 通过 flowable.database-schema-update=true 自动创建，
--      不在此迁移脚本中维护。

ALTER TABLE apply_order
    ADD COLUMN process_instance_id VARCHAR(64) NULL COMMENT 'Flowable 流程实例ID（NULL=未接入工作流）' AFTER status;

-- 按流程实例反查申请单（如待办列表、流程追溯）
CREATE INDEX idx_apply_order_process_instance ON apply_order (process_instance_id);

-- 按申请编号反查（历史数据多为空，但便于后续排障）
-- 说明：apply_no 在 V1 建表时未建唯一索引，此处不额外添加，避免历史重复数据导致迁移失败。
