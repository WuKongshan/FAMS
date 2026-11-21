package com.example.asset.modules.check.dto;

import lombok.Data;

/**
 * LLM 返回的单条分析结果
 * 用于接收 DeepSeek API 返回的 JSON 数组元素
 */
@Data
public class LLMAnalysisItem {

    /** 盘点记录ID，与请求中的 recordId 对应 */
    private Long recordId;

    /** LLM 生成的差异原因分析 */
    private String analysis;

    /** LLM 生成的处理建议 */
    private String suggestion;
}
