-- 回填已有业务表的部门字段（V4/V5 新增列后历史数据为 NULL）
-- 根据申请人/报修人ID从用户表反查部门信息

-- 1. 报修工单（V5 新增 dept_id/dept_name）
UPDATE repair_order r
    INNER JOIN sys_user u ON r.reporter_id = u.id
SET r.dept_id   = u.dept_id,
    r.dept_name = u.dept_name
WHERE r.dept_id IS NULL
  AND r.reporter_id IS NOT NULL;

UPDATE repair_order r
    INNER JOIN sys_user u ON r.create_by = u.id
SET r.dept_id   = u.dept_id,
    r.dept_name = u.dept_name
WHERE r.dept_id IS NULL
  AND r.reporter_id IS NULL
  AND r.create_by IS NOT NULL;

-- 2. 领用/归还申请单（V4 新增 dept_id/dept_name，如有历史 NULL 一并修复）
UPDATE apply_order a
    INNER JOIN sys_user u ON a.apply_user_id = u.id
SET a.dept_id   = u.dept_id,
    a.dept_name = u.dept_name
WHERE a.dept_id IS NULL
  AND a.apply_user_id IS NOT NULL;

UPDATE apply_order a
    INNER JOIN sys_user u ON a.create_by = u.id
SET a.dept_id   = u.dept_id,
    a.dept_name = u.dept_name
WHERE a.dept_id IS NULL
  AND a.apply_user_id IS NULL
  AND a.create_by IS NOT NULL;

-- 3. 报废申请单（V4 新增 dept_id/dept_name，如有历史 NULL 一并修复）
UPDATE scrap_order s
    INNER JOIN sys_user u ON s.apply_user_id = u.id
SET s.dept_id   = u.dept_id,
    s.dept_name = u.dept_name
WHERE s.dept_id IS NULL
  AND s.apply_user_id IS NOT NULL;

UPDATE scrap_order s
    INNER JOIN sys_user u ON s.create_by = u.id
SET s.dept_id   = u.dept_id,
    s.dept_name = u.dept_name
WHERE s.dept_id IS NULL
  AND s.apply_user_id IS NULL
  AND s.create_by IS NOT NULL;
