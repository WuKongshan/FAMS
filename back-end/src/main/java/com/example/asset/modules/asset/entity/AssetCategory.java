package com.example.asset.modules.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.asset.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 资产分类表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asset_category")
public class AssetCategory extends BaseEntity {

    /** 分类名称 */
    private String categoryName;

    /** 上级分类ID */
    private Long parentId;

    /** 排序 */
    private Integer sort;

    /** 状态 */
    private Integer status;
}
