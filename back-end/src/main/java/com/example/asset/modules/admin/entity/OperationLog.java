package com.example.asset.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作审计日志实体 - 记录所有业务操作，满足协同工作审计追溯要求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operation_log")
public class OperationLog extends BaseEntity {

    /** 操作模块（如：用户管理、资产管理、审批管理等） */
    private String module;

    /** 操作类型（如：新增、修改、删除、审批、登录等） */
    private String operation;

    /** 操作描述（面向审计人员的可读描述） */
    private String description;

    /** 请求方法（GET/POST/PUT/DELETE） */
    private String requestMethod;

    /** 请求URL */
    private String requestUrl;

    /** 请求参数（JSON格式，敏感字段已脱敏） */
    private String requestParams;

    /** 响应结果摘要 */
    private String responseResult;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作人IP地址 */
    private String operatorIp;

    /** 操作时间（精确到秒） */
    private LocalDateTime operationTime;

    /** 操作耗时（毫秒） */
    private Long costTime;

    /** 操作状态：1=成功 0=失败 */
    private Integer status;

    /** 错误信息（操作失败时记录） */
    private String errorMsg;
}
