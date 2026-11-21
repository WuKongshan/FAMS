-- ============================================
-- 高校固定资产协同管理系统 - 数据库建表脚本
-- Database: asset_management
-- ============================================
-- ⚠️ 已废弃：此文件已被 Flyway 迁移替代
-- 迁移文件位于: db/migration/V1__init_schema.sql
-- 启动应用时 Flyway 自动执行迁移，无需手动导入此文件
-- 此文件保留仅供参考表结构
-- ============================================

CREATE DATABASE IF NOT EXISTS asset_management
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE asset_management;

-- ============================================
-- 1. 系统管理模块
-- ============================================

-- 部门/学院表
CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    dept_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    parent_id BIGINT DEFAULT 0 COMMENT '上级部门ID',
    sort INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门/学院表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    description VARCHAR(255) COMMENT '角色描述',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    real_name VARCHAR(50) COMMENT '真实姓名',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    dept_id BIGINT COMMENT '所属学院/部门ID',
    dept_name VARCHAR(100) COMMENT '所属学院/部门名称',
    role_id BIGINT COMMENT '角色ID',
    status TINYINT DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================
-- 2. 资产管理模块
-- ============================================

-- 资产分类表
CREATE TABLE IF NOT EXISTS asset_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
    parent_id BIGINT DEFAULT 0 COMMENT '上级分类ID',
    sort INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产分类表';

-- 资产信息表
CREATE TABLE IF NOT EXISTS asset_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    asset_code VARCHAR(50) NOT NULL COMMENT '资产编号（唯一）',
    asset_name VARCHAR(200) NOT NULL COMMENT '资产名称',
    quantity INT DEFAULT 1 COMMENT '资产数量',
    category_id BIGINT COMMENT '分类ID',
    category_name VARCHAR(100) COMMENT '分类名称',
    model VARCHAR(200) COMMENT '规格型号',
    purchase_date DATE COMMENT '购置时间',
    original_value DECIMAL(12,2) COMMENT '原值',
    location VARCHAR(200) COMMENT '存放地点',
    dept_id BIGINT COMMENT '归属部门ID',
    dept_name VARCHAR(100) COMMENT '归属部门名称',
    user_id BIGINT COMMENT '使用人ID',
    user_name VARCHAR(50) COMMENT '使用人姓名',
    status VARCHAR(20) DEFAULT '闲置' COMMENT '资产状态：在用/闲置/维修中/已报废',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_asset_code (asset_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产信息表';

-- ============================================
-- 3. 领用/归还审批模块
-- ============================================

-- 申请单表
CREATE TABLE IF NOT EXISTS apply_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    apply_no VARCHAR(50) COMMENT '申请编号',
    apply_type VARCHAR(20) NOT NULL COMMENT '申请类型：BORROW=领用 RETURN=归还',
    asset_id BIGINT NOT NULL COMMENT '资产ID',
    asset_code VARCHAR(50) COMMENT '资产编号',
    asset_name VARCHAR(200) COMMENT '资产名称',
    apply_user_id BIGINT COMMENT '申请人ID',
    apply_user_name VARCHAR(50) COMMENT '申请人姓名',
    dept_id BIGINT COMMENT '申请人所属部门ID',
    dept_name VARCHAR(100) COMMENT '申请人所属部门名称',
    start_date DATE COMMENT '使用期限起始',
    end_date DATE COMMENT '使用期限截止',
    purpose VARCHAR(500) COMMENT '用途说明',
    status VARCHAR(30) DEFAULT 'PENDING_FIRST' COMMENT '审批状态',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领用/归还申请表';

-- 审批记录表
CREATE TABLE IF NOT EXISTS approval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    apply_id BIGINT NOT NULL COMMENT '申请单ID',
    apply_type VARCHAR(20) COMMENT '申请类型',
    approve_level VARCHAR(20) COMMENT '审批层级：FIRST=初审 SECOND=复审',
    approve_user_id BIGINT COMMENT '审批人ID',
    approve_user_name VARCHAR(50) COMMENT '审批人姓名',
    result VARCHAR(20) COMMENT '审批结果：PASS=通过 REJECT=驳回',
    comment VARCHAR(500) COMMENT '审批意见',
    approve_time DATETIME COMMENT '审批时间',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- ============================================
-- 4. 协同盘点模块
-- ============================================

-- 盘点任务表
CREATE TABLE IF NOT EXISTS check_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_no VARCHAR(50) COMMENT '任务编号',
    task_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    dept_ids VARCHAR(500) COMMENT '盘点范围-部门ID列表',
    dept_names VARCHAR(500) COMMENT '盘点范围-部门名称',
    category_ids VARCHAR(500) COMMENT '盘点范围-资产分类ID列表',
    start_date DATE COMMENT '盘点开始时间',
    end_date DATE COMMENT '盘点截止时间',
    participant_ids VARCHAR(500) COMMENT '参与人员ID列表',
    participant_names VARCHAR(500) COMMENT '参与人员姓名',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '任务状态：PENDING=待开始 IN_PROGRESS=进行中 COMPLETED=已完成',
    publisher_id BIGINT COMMENT '发布人ID',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点任务表';

-- 盘点记录表
CREATE TABLE IF NOT EXISTS check_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '盘点任务ID',
    asset_id BIGINT COMMENT '资产ID',
    asset_code VARCHAR(50) COMMENT '资产编号',
    asset_name VARCHAR(200) COMMENT '资产名称',
    book_quantity INT DEFAULT 1 COMMENT '账面数量',
    actual_quantity INT DEFAULT 0 COMMENT '实盘数量',
    diff_quantity INT DEFAULT 0 COMMENT '差异数量',
    result VARCHAR(20) COMMENT '盘点结果：MATCH=相符 SURPLUS=盘盈 SHORTAGE=盘亏',
    remark VARCHAR(500) COMMENT '备注',
    checker_id BIGINT COMMENT '盘点人ID',
    checker_name VARCHAR(50) COMMENT '盘点人姓名',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点记录表';

-- ============================================
-- 5. 报修工单模块
-- ============================================

CREATE TABLE IF NOT EXISTS repair_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_no VARCHAR(50) COMMENT '工单编号',
    asset_id BIGINT COMMENT '资产ID',
    asset_code VARCHAR(50) COMMENT '资产编号',
    asset_name VARCHAR(200) COMMENT '资产名称',
    reporter_id BIGINT COMMENT '报修人ID',
    reporter_name VARCHAR(50) COMMENT '报修人姓名',
    fault_desc TEXT COMMENT '故障描述',
    images VARCHAR(1000) COMMENT '故障图片URL（逗号分隔）',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '工单状态',
    repairer_id BIGINT COMMENT '维修人ID',
    repairer_name VARCHAR(50) COMMENT '维修人姓名',
    repair_result TEXT COMMENT '维修结果描述',
    repair_cost DECIMAL(10,2) COMMENT '维修费用',
    accept_time DATETIME COMMENT '接单时间',
    complete_time DATETIME COMMENT '完成时间',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报修工单表';

-- ============================================
-- 6. 资产报废模块
-- ============================================

CREATE TABLE IF NOT EXISTS scrap_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    scrap_no VARCHAR(50) COMMENT '申请编号',
    asset_id BIGINT NOT NULL COMMENT '资产ID',
    asset_code VARCHAR(50) COMMENT '资产编号',
    asset_name VARCHAR(200) COMMENT '资产名称',
    apply_user_id BIGINT COMMENT '申请人ID',
    apply_user_name VARCHAR(50) COMMENT '申请人姓名',
    dept_id BIGINT COMMENT '申请人所属部门ID',
    dept_name VARCHAR(100) COMMENT '申请人所属部门名称',
    reason TEXT COMMENT '报废原因',
    status VARCHAR(30) DEFAULT 'PENDING_FIRST' COMMENT '审批状态',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '修改人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产报废申请表';

-- ============================================
-- 7. 操作日志表（审计追踪）
-- ============================================
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    module VARCHAR(50) COMMENT '操作模块',
    operation VARCHAR(50) COMMENT '操作类型',
    description VARCHAR(500) COMMENT '操作描述',
    request_method VARCHAR(10) COMMENT '请求方法（GET/POST/PUT/DELETE）',
    request_url VARCHAR(500) COMMENT '请求URL',
    request_params TEXT COMMENT '请求参数（JSON，密码等敏感字段已脱敏）',
    response_result VARCHAR(2000) COMMENT '响应结果摘要',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(50) COMMENT '操作人姓名',
    operator_ip VARCHAR(50) COMMENT '操作人IP地址',
    operation_time DATETIME COMMENT '操作时间',
    cost_time BIGINT COMMENT '耗时（毫秒）',
    status TINYINT DEFAULT 1 COMMENT '操作状态：1=成功 0=失败',
    error_msg VARCHAR(2000) COMMENT '错误信息（仅失败时记录）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by BIGINT COMMENT '创建人ID',
    update_by BIGINT COMMENT '更新人ID',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_operator_id (operator_id),
    INDEX idx_module (module),
    INDEX idx_operation_time (operation_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志表';
