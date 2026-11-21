-- ============================================
-- 高校固定资产协同管理系统 - 初始化数据脚本
-- Database: asset_management
-- 所有密码均为 123456（BCrypt 加密）
-- ============================================
-- ⚠️ 已废弃：此文件已被 Flyway 迁移替代
-- 迁移文件位于: db/migration/V2__init_data.sql
-- 启动应用时 Flyway 自动执行迁移，无需手动导入此文件
-- 此文件保留仅供参考初始数据
-- ============================================

USE asset_management;

-- ============================================
-- 初始化部门/学院数据
-- ============================================
INSERT INTO sys_department (id, dept_name, parent_id, sort, status) VALUES
(1, '计算机科学与技术学院', 0, 1, 1),
(2, '电子信息工程学院',       0, 2, 1),
(3, '机械工程学院',           0, 3, 1),
(4, '经济管理学院',           0, 4, 1),
(5, '外国语学院',             0, 5, 1),
(6, '资产管理处（校级）',     0, 99, 1);

-- ============================================
-- 初始化角色数据
-- ============================================
INSERT INTO sys_role (id, role_name, role_code, description, status) VALUES
(1, '超级管理员',       'ROLE_ADMIN',      '校级管理员，拥有全部权限',             1),
(2, '学院资产管理员',   'ROLE_DEPT_ADMIN', '学院级别资产管理员，管理本学院资产',   1),
(3, '普通师生',         'ROLE_USER',       '普通用户，可申请领用/归还/报修/报废',  1);

-- ============================================
-- 初始化用户数据
-- ============================================
-- 密码 BCrypt: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi
INSERT INTO sys_user (id, username, password, real_name, phone, email, dept_id, dept_name, role_id, status) VALUES
-- 校级超级管理员
(1,  'admin',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '13800000001', 'admin@university.edu.cn',      6, '资产管理处（校级）',       1, 1),

-- 计算机科学与技术学院（1 管理员 + 3 师生）
(2,  'zhangwei', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张伟',       '13800000002', 'zhangwei@university.edu.cn',  1, '计算机科学与技术学院',     2, 1),
(3,  'liuyang',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '刘洋',       '13800000003', 'liuyang@university.edu.cn',   1, '计算机科学与技术学院',     3, 1),
(4,  'chenjing', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '陈静',       '13800000004', 'chenjing@university.edu.cn',  1, '计算机科学与技术学院',     3, 1),
(5,  'wanghao',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王浩',       '13800000005', 'wanghao@university.edu.cn',   1, '计算机科学与技术学院',     3, 1),

-- 电子信息工程学院（1 管理员 + 3 师生）
(6,  'zhaoming', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵明',       '13800000006', 'zhaoming@university.edu.cn',  2, '电子信息工程学院',         2, 1),
(7,  'sunli',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '孙丽',       '13800000007', 'sunli@university.edu.cn',     2, '电子信息工程学院',         3, 1),
(8,  'zhoujie',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '周杰',       '13800000008', 'zhoujie@university.edu.cn',   2, '电子信息工程学院',         3, 1),
(9,  'wulei',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '吴磊',       '13800000009', 'wulei@university.edu.cn',     2, '电子信息工程学院',         3, 1),

-- 机械工程学院（1 管理员 + 2 师生）
(10, 'huangqiang','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi','黄强',       '13800000010', 'huangqiang@university.edu.cn',3, '机械工程学院',             2, 1),
(11, 'malin',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '马琳',       '13800000011', 'malin@university.edu.cn',     3, '机械工程学院',             3, 1),
(12, 'xuyang',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '徐洋',       '13800000012', 'xuyang@university.edu.cn',    3, '机械工程学院',             3, 1),

-- 经济管理学院（1 管理员 + 2 师生）
(13, 'linxia',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '林霞',       '13800000013', 'linxia@university.edu.cn',    4, '经济管理学院',             2, 1),
(14, 'guowei',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '郭伟',       '13800000014', 'guowei@university.edu.cn',    4, '经济管理学院',             3, 1),
(15, 'tangfang', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '唐芳',       '13800000015', 'tangfang@university.edu.cn',  4, '经济管理学院',             3, 1),

-- 外国语学院（1 管理员 + 2 师生）
(16, 'yangfan',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '杨帆',       '13800000016', 'yangfan@university.edu.cn',   5, '外国语学院',               2, 1),
(17, 'hexue',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '何雪',       '13800000017', 'hexue@university.edu.cn',     5, '外国语学院',               3, 1),
(18, 'duanpeng', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '段鹏',       '13800000018', 'duanpeng@university.edu.cn',  5, '外国语学院',               3, 1);

-- ============================================
-- 初始化资产分类数据（两级树形）
-- ============================================
INSERT INTO asset_category (id, category_name, parent_id, sort, status) VALUES
(1,  '设备类',     0, 1, 1),
(2,  '家具类',     0, 2, 1),
(3,  '实验器材',   0, 3, 1),
(4,  '软件类',     0, 4, 1),
(5,  '计算机设备', 1, 1, 1),
(6,  '打印设备',   1, 2, 1),
(7,  '办公桌椅',   2, 1, 1),
(8,  '实验仪器',   3, 1, 1);

-- ============================================
-- 初始化资产数据（每个学院 3~4 件，多种状态）
-- ============================================
INSERT INTO asset_info (id, asset_code, asset_name, category_id, category_name, model, purchase_date, original_value, location, dept_id, dept_name, user_id, user_name, status) VALUES
-- 计算机科学与技术学院
(1,  'AST-2024-001', '联想ThinkPad X1笔记本',   5, '计算机设备', 'X1 Carbon Gen11',    '2024-01-15', 8999.00,  '计算机学院楼302室',  1, '计算机科学与技术学院', 3,  '刘洋', '在用'),
(2,  'AST-2024-002', 'HP LaserJet 打印机',      6, '打印设备',   'M404dn',             '2024-02-20', 3500.00,  '计算机学院楼105室',  1, '计算机科学与技术学院', NULL, NULL,  '闲置'),
(3,  'AST-2024-003', 'Dell PowerEdge 服务器',   5, '计算机设备', 'PowerEdge R750',     '2024-03-10', 45000.00, '计算机学院机房A',    1, '计算机科学与技术学院', 4,  '陈静', '在用'),
(4,  'AST-2023-010', '投影仪 EPSON CB-X51',     5, '计算机设备', 'CB-X51',             '2023-09-15', 6500.00,  '计算机学院多媒体室', 1, '计算机科学与技术学院', NULL, NULL,  '维修中'),

-- 电子信息工程学院
(5,  'AST-2024-004', '实验用示波器 Tektronix',   8, '实验仪器',   'TBS2000B',          '2024-04-05', 12800.00, '电子学院实验室A',    2, '电子信息工程学院', 7,  '孙丽', '在用'),
(6,  'AST-2024-005', '信号发生器 Agilent',       8, '实验仪器',   '33220A',             '2024-04-10', 8600.00,  '电子学院实验室A',    2, '电子信息工程学院', NULL, NULL,  '闲置'),
(7,  'AST-2024-006', '频谱分析仪 Keysight',      8, '实验仪器',   'N9320B',             '2024-05-20', 35000.00, '电子学院实验室B',    2, '电子信息工程学院', 8,  '周杰', '在用'),
(8,  'AST-2022-005', '旧式逻辑分析仪',            8, '实验仪器',   'LA-200',             '2022-03-15', 4200.00,  '电子学院仓库',       2, '电子信息工程学院', NULL, NULL,  '已报废'),

-- 机械工程学院
(9,  'AST-2024-007', 'CNC数控机床',              8, '实验仪器',   'VMC850',             '2024-06-01', 180000.00,'机械学院实训车间',   3, '机械工程学院', 11, '马琳', '在用'),
(10, 'AST-2024-008', '3D打印机 Ultimaker',       8, '实验仪器',   'S5 Pro',             '2024-06-15', 42000.00, '机械学院创新实验室', 3, '机械工程学院', 12, '徐洋', '在用'),
(11, 'AST-2024-009', '办公桌套装 L型',           7, '办公桌椅',   'L-200',              '2024-05-12', 2500.00,  '机械学院楼201室',    3, '机械工程学院', NULL, NULL,  '闲置'),

-- 经济管理学院
(12, 'AST-2024-010', '联想台式机启天M450',       5, '计算机设备', '启天M450',           '2024-07-10', 5200.00,  '经管学院楼305室',    4, '经济管理学院', 14, '郭伟', '在用'),
(13, 'AST-2024-011', 'HP LaserJet MFP 一体机',   6, '打印设备',   'M437n',              '2024-07-15', 4200.00,  '经管学院楼公共区',   4, '经济管理学院', NULL, NULL,  '闲置'),
(14, 'AST-2024-012', '会议桌套装',               7, '办公桌椅',   'HY-4800',            '2024-08-01', 8800.00,  '经管学院会议室',     4, '经济管理学院', NULL, NULL,  '在用'),

-- 外国语学院
(15, 'AST-2024-013', '语言实验室终端 Dell',      5, '计算机设备', 'OptiPlex 5000',      '2024-08-20', 6200.00,  '外语学院语音室A',    5, '外国语学院', 17, '何雪', '在用'),
(16, 'AST-2024-014', '同声传译系统',             8, '实验仪器',   'TS-800',             '2024-09-01', 96000.00, '外语学院国际会议厅', 5, '外国语学院', NULL, NULL,  '在用'),
(17, 'AST-2024-015', 'EPSON 投影仪',             5, '计算机设备', 'CB-L530U',           '2024-09-10', 12500.00, '外语学院多媒体教室', 5, '外国语学院', 18, '段鹏', '维修中');
