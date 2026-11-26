package com.example.asset.modules.check.dto;

import lombok.Data;

/**
 * 分析服务返回的单条结果
 * 对应 llm-service 响应中的 {@code items[]} 元素
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
