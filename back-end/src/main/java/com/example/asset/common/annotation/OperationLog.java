package com.example.asset.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解 - 标记需要自动记录审计日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 操作模块（如：用户管理、资产管理、审批管理等） */
    String module();

    /** 操作类型（如：新增、修改、删除、审批、登录等） */
    String operation();

    /** 操作描述（面向审计人员的可读描述，为空时自动使用 Swagger @Operation summary） */
    String description() default "";

    /** 是否记录请求参数（默认 true，查询类操作可设为 false） */
    boolean recordParams() default true;

    /** 是否记录响应结果（默认 true，敏感接口可设为 false） */
    boolean recordResult() default true;
}
