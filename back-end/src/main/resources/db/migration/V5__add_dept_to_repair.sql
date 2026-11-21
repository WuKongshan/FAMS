-- 为报修工单表增加部门字段，支持院系级数据隔离
ALTER TABLE repair_order
    ADD COLUMN dept_id BIGINT COMMENT '报修人所属部门ID' AFTER reporter_name,
    ADD COLUMN dept_name VARCHAR(100) COMMENT '报修人所属部门名称' AFTER dept_id;
