package com.example.asset.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class User extends BaseEntity {

    /** 用户名 */
    private String username;

    /** 密码（仅写入，JSON序列化时隐藏） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 所属学院/部门ID */
    private Long deptId;

    /** 所属学院/部门名称 */
    private String deptName;

    /** 角色ID：1=超级管理员 2=学院管理员 3=普通师生 */
    private Long roleId;

    /** 状态：1=启用 0=禁用 */
    private Integer status;
}
