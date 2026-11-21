package com.example.asset.modules.check.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 盘点任务表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_task")
public class CheckTask extends BaseEntity {

    /** 任务编号 */
    private String taskNo;

    /** 任务名称 */
    private String taskName;

    /** 盘点范围-部门ID（逗号分隔） */
    private String deptIds;

    /** 盘点范围-部门名称 */
    private String deptNames;

    /** 盘点范围-资产分类ID */
    private String categoryIds;

    /** 盘点开始时间 */
    private LocalDate startDate;

    /** 盘点截止时间 */
    private LocalDate endDate;

    /** 参与人员ID（逗号分隔） */
    private String participantIds;

    /** 参与人员姓名 */
    private String participantNames;

    /** 任务状态：PENDING=待开始 IN_PROGRESS=进行中 COMPLETED=已完成 */
    private String status;

    /** 发布人ID */
    private Long publisherId;
}
