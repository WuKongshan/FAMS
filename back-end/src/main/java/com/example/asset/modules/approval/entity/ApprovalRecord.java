package com.example.asset.modules.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 审批记录表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("approval_record")
public class ApprovalRecord extends BaseEntity {

    /** 申请单ID */
    private Long applyId;

    /** 申请类型 */
    private String applyType;

    /** 审批层级：FIRST=初审 SECOND=复审 */
    private String approveLevel;

    /** 审批人ID */
    private Long approveUserId;

    /** 审批人姓名 */
    private String approveUserName;

    /** 审批结果：PASS=通过 REJECT=驳回 */
    private String result;

    /** 审批意见 */
    private String comment;

    /** 审批时间 */
    private LocalDateTime approveTime;
}
