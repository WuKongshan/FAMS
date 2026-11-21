-- 为审批相关表增加部门字段，支持院系级数据隔离
-- apply_order: 领用/归还申请单
-- scrap_order: 报废申请单

ALTER TABLE apply_order
    ADD COLUMN dept_id BIGINT COMMENT '申请人所属部门ID' AFTER apply_user_name,
    ADD COLUMN dept_name VARCHAR(100) COMMENT '申请人所属部门名称' AFTER dept_id;

ALTER TABLE scrap_order
    ADD COLUMN dept_id BIGINT COMMENT '申请人所属部门ID' AFTER apply_user_name,
    ADD COLUMN dept_name VARCHAR(100) COMMENT '申请人所属部门名称' AFTER dept_id;
