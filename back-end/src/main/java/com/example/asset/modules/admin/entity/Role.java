package com.example.asset.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class Role extends BaseEntity {

    /** 角色名称 */
    private String roleName;

    /** 角色编码（ROLE_ADMIN / ROLE_DEPT_ADMIN / ROLE_USER） */
    private String roleCode;

    /** 角色描述 */
    private String description;

    /** 状态 */
    private Integer status;
}
