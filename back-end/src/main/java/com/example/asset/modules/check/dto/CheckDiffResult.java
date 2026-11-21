package com.example.asset.modules.check.dto;

import lombok.Data;

/** 盘点差异分析结果 */
@Data
public class CheckDiffResult {

    private Long recordId;
    private String assetCode;
    private String assetName;
    private Integer bookQuantity;
    private Integer actualQuantity;
    private Integer diffQuantity;
    private String analysis;
    private String suggestion;
}
