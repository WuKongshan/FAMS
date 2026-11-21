-- 为 asset_info 表增加数量字段
ALTER TABLE asset_info ADD COLUMN quantity INT DEFAULT 1 COMMENT '资产数量' AFTER asset_name;
