package com.example.asset.modules.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 领用/归还申请表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("apply_order")
public class ApplyOrder extends BaseEntity {

    /** 申请编号 */
    private String applyNo;

    /** 申请类型：BORROW=领用 RETURN=归还 */
    private String applyType;

    /** 资产ID */
    private Long assetId;

    /** 资产编号 */
    private String assetCode;

    /** 资产名称 */
    private String assetName;

    /** 申请人ID */
    private Long applyUserId;

    /** 申请人姓名 */
    private String applyUserName;

    /** 申请人所属部门ID */
    private Long deptId;

    /** 申请人所属部门名称 */
    private String deptName;

    /** 使用期限起始 */
    private LocalDate startDate;

    /** 使用期限截止 */
    private LocalDate endDate;

    /** 用途说明 */
    private String purpose;

    /** 审批状态：PENDING_FIRST=待初审 FIRST_PASS=初审通过 FIRST_REJECT=初审驳回 PENDING_SECOND=待复审 SECOND_PASS=复审通过 SECOND_REJECT=复审驳回 COMPLETED=已完成 */
    private String status;

    /**
     * Flowable 流程实例ID。
     * <p>非空 → 该申请单由 Flowable 流程实例驱动，审批按 BPMN 节点流转；</p>
     * <p>为空 → 未接入工作流，审批走 {@code ApprovalServiceImpl} 中的原有状态机分支。</p>
     */
    private String processInstanceId;
}
