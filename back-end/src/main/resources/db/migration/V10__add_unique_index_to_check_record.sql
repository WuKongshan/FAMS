-- ============================================
-- Flyway V10: 盘点记录 (task_id, asset_id) 唯一约束
-- ============================================
-- 背景：saveCheckRecord 的写入路径是「查无 ID → insert」，本身没有并发保护。
-- 两个盘点人（或一次误双击）同时为准入同一资产时，两个请求都在插入前查不到记录，
-- 于是各插一行，同一资产在差异分析和汇总统计里被算两次。
-- 唯一索引是把这个并发窗口堵住的唯一可靠位置：应用层的"先查后写"永远有间隙。
--
-- 已知边界（重要，不要误读）：
--   MySQL 唯一索引对 NULL 不生效 —— 多行 NULL 被视为互不相同。
--   而 check_record.asset_id 是可空的：asset_id IS NULL 表示「手工自由录入、未匹配到台账资产」，
--   同一任务下出现多行这样的记录是正常业务（存量 26 行里有 19 行为 NULL）。
--   因此本索引天然不覆盖 asset_id IS NULL 的行，这类行理论上仍可能重复。
--   要彻底约束需要产品先确认「盘点记录必须绑定资产」，属于业务规则变更，不在本次范围。
--
-- 幂等性：清理语句是「自连接删除 id 较大的重复行」，在已无重复的库上影响 0 行，可安全重跑。
-- 新增索引部分由 Flyway 版本管理保证只执行一次
-- （MySQL 不支持 ALTER TABLE ... ADD INDEX IF NOT EXISTS，无法在 SQL 层面做重复保护）。

-- 步骤 1：清理存量真正的重复行 —— 每组 (task_id, asset_id) 只保留 id 最小的那一行。
-- 必须显式排除 asset_id IS NULL：那些不是重复数据，而是同一任务下不同资产的手工录入，
-- 删掉任何一行都是数据事故（虽然 NULL 比较本就不会命中，这里写明是为了让意图不可误解）。
DELETE cr
FROM check_record cr
         INNER JOIN check_record kept
                    ON cr.task_id = kept.task_id
                        AND cr.asset_id = kept.asset_id
                        AND cr.id > kept.id
WHERE cr.asset_id IS NOT NULL;

-- 步骤 2：把并发重复永久挡在数据库层。
-- 索引同时承担「按任务+资产反查记录」的查询加速作用（原表除主键外无任何索引）。
ALTER TABLE check_record
    ADD UNIQUE KEY uk_check_record_task_asset (task_id, asset_id);
