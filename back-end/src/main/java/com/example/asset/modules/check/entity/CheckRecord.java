package com.example.asset.modules.check.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 盘点记录表（实际盘点数据）
 * 支持多人在线协同录入
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_record")
public class CheckRecord extends BaseEntity {

    /** 盘点任务ID */
    private Long taskId;

    /** 资产ID */
    private Long assetId;

    /** 资产编号 */
    private String assetCode;

    /** 资产名称 */
    private String assetName;

    /** 账面数量 */
    private Integer bookQuantity;

    /** 实盘数量 */
    private Integer actualQuantity;

    /** 差异数量 */
    private Integer diffQuantity;

    /** 盘点结果：MATCH=相符 SURPLUS=盘盈 SHORTAGE=盘亏 */
    private String result;

    /** 备注 */
    private String remark;

    /** 盘点人ID */
    private Long checkerId;

    /** 盘点人姓名 */
    private String checkerName;

    /**
     * 差异处置状态：NULL=非差异记录（相符，不参与处置） PENDING=待处置 HANDLED=已处置
     */
    private String diffHandleStatus;

    /** 处置方式：SCRAP=转报废 ASSET_ADD=补登记 MANUAL=人工确认 */
    private String diffHandleType;

    /** 处置时间 */
    private LocalDateTime diffHandleTime;

    /** 处置人ID */
    private Long diffHandleBy;
}
