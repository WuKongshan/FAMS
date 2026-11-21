package com.example.asset.modules.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 资产表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asset_info")
public class Asset extends BaseEntity {

    /** 资产编号（唯一） */
    private String assetCode;

    /** 资产名称 */
    private String assetName;

    /** 资产数量 */
    private Integer quantity;

    /** 分类ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 规格型号 */
    private String model;

    /** 购置时间 */
    private LocalDate purchaseDate;

    /** 原值 */
    private BigDecimal originalValue;

    /** 存放地点 */
    private String location;

    /** 归属部门ID */
    private Long deptId;

    /** 归属部门名称 */
    private String deptName;

    /** 使用人ID */
    private Long userId;

    /** 使用人姓名 */
    private String userName;

    /** 状态：在用/闲置/维修中/已报废 */
    private String status;
}
