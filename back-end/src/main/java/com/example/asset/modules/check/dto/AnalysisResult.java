package com.example.asset.modules.check.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 一次差异分析的完整结果。
 *
 * <p>除了条目本身，还带上 {@code engine} —— 标明这批结论是模型给的还是模板生成的。
 * 少了它，LLM 服务不可用时的降级就无法与真实分析区分，前端也无从提示用户。
 */
@Data
@AllArgsConstructor
public class AnalysisResult {

    private List<CheckDiffResult> items;

    /** llm / rule / mixed */
    private String engine;
}
