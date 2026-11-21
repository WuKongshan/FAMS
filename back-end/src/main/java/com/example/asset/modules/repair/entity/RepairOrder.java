package com.example.asset.modules.repair.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 报修工单表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repair_order")
public class RepairOrder extends BaseEntity {

    /** 工单编号 */
    private String orderNo;

    /** 资产ID */
    private Long assetId;

    /** 资产编号 */
    private String assetCode;

    /** 资产名称 */
    private String assetName;

    /** 报修人ID */
    private Long reporterId;

    /** 报修人姓名 */
    private String reporterName;

    /** 报修人所属部门ID */
    private Long deptId;

    /** 报修人所属部门名称 */
    private String deptName;

    /** 故障描述 */
    private String faultDesc;

    /** 故障图片URL（逗号分隔） */
    private String images;

    /** 工单状态：PENDING_FIRST=待初审 FIRST_REJECT=初审驳回 IN_PROGRESS=维修中 COMPLETED=维修完成 */
    private String status;

    /** 维修人ID */
    private Long repairerId;

    /** 维修人姓名 */
    private String repairerName;

    /** 维修结果描述 */
    private String repairResult;

    /** 维修费用 */
    private BigDecimal repairCost;

    /** 接单时间 */
    private LocalDateTime acceptTime;

    /** 完成时间 */
    private LocalDateTime completeTime;
}
