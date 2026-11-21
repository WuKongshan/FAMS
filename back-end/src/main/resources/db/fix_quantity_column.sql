-- ============================================
-- 紧急修复：为 asset_info 表添加 quantity 列
-- 如果资产台账页面显示空白，执行此 SQL
-- ============================================
-- 检查列是否已存在，不存在则添加
SET @dbname = DATABASE();
SET @exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @dbname AND table_name = 'asset_info' AND column_name = 'quantity');

SET @sql = IF(@exists = 0,
    'ALTER TABLE asset_info ADD COLUMN quantity INT DEFAULT 1 COMMENT ''资产数量'' AFTER asset_name',
    'SELECT ''quantity 列已存在，无需添加'' AS msg');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证
SELECT '修复完成，当前 asset_info 数据：' AS msg;
SELECT id, asset_code, asset_name, quantity, status FROM asset_info;
