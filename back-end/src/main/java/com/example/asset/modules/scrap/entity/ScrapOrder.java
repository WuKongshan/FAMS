package com.example.asset.modules.scrap.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 报废申请表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scrap_order")
public class ScrapOrder extends BaseEntity {

    /** 申请编号 */
    private String scrapNo;

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

    /** 报废原因 */
    private String reason;

    /** 审批状态：PENDING_FIRST=待初审 FIRST_PASS=初审通过 FIRST_REJECT=初审驳回 PENDING_SECOND=待复审 SECOND_PASS=复审通过 SECOND_REJECT=复审驳回 COMPLETED=已完成 */
    private String status;

    /**
     * Flowable 流程实例ID。
     * <p>非空 → 由 Flowable 流程实例驱动，审批按 BPMN 节点流转；</p>
     * <p>为空 → 未接入工作流，审批走 {@code ScrapServiceImpl} 原有的状态机分支。</p>
     */
    private String processInstanceId;
}
