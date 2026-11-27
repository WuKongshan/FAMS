-- ============================================
-- Flyway V12: 盘点差异处置闭环 + 分析结果来源标记
-- ============================================
-- 两件事合并在这一个脚本里，因为它们同属一类问题：系统里发生了值得注意的事，
-- 但没有任何地方告诉使用者。
--
-- 一、差异处置状态（check_record）
-- 背景：确认盘点改成"只标记差异、不碰资产台账"之后（见 2026-11-26 的改动），
-- 盘亏的资产会一直停在原状态，系统里没有任何地方提示"这条差异还没处理"。
-- 管理员只能靠记忆或翻记录。
--
-- 状态语义：
--   NULL    = 非差异记录（result = 'MATCH'），不参与处置流程
--   PENDING = 待处置
--   HANDLED = 已处置
--
-- 处置方式（diff_handle_type）只是标签，不直接改资产：
--   SCRAP     转报废单（走报废审批）
--   ASSET_ADD 补登记新增资产（盘盈）
--   MANUAL    人工核实确认（如借出未还、登记错误）
--
-- 二、分析结果来源（analysis_job）
-- 背景：llm-service 不可用时，Java 侧会回退到模板文案，作业状态仍是 SUCCESS。
-- 用户看到"分析完成"却无法判断结论是模型给的还是模板生成的 —— 这是静默降级。
-- 记录 engine 后，前端可以明确提示"本次为规则生成，未使用 AI"。

ALTER TABLE check_record
    ADD COLUMN diff_handle_status VARCHAR(20) NULL COMMENT 'NULL=非差异记录 PENDING=待处置 HANDLED=已处置',
    ADD COLUMN diff_handle_type   VARCHAR(20) NULL COMMENT 'SCRAP=转报废 ASSET_ADD=补登记 MANUAL=人工确认',
    ADD COLUMN diff_handle_time   DATETIME    NULL COMMENT '处置时间',
    ADD COLUMN diff_handle_by     BIGINT      NULL COMMENT '处置人';

-- 存量差异记录统一置为待处置；相符记录保持 NULL（不参与处置流程）
UPDATE check_record
SET diff_handle_status = 'PENDING'
WHERE result IS NOT NULL
  AND result <> 'MATCH'
  AND diff_handle_status IS NULL;

ALTER TABLE analysis_job
    ADD COLUMN engine VARCHAR(20) NULL COMMENT 'llm / rule / mixed，标明这批结论由谁产出';
