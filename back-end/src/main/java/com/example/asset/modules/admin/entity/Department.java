package com.example.asset.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 部门/学院表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_department")
public class Department extends BaseEntity {

    /** 部门名称 */
    private String deptName;

    /** 上级部门ID */
    private Long parentId;

    /** 排序 */
    private Integer sort;

    /** 状态 */
    private Integer status;
}
