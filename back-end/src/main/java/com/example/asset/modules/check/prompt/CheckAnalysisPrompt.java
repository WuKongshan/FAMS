package com.example.asset.modules.check.prompt;

import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;

/**
 * 盘点差异分析 Prompt 模板
 * 构造发送给 LLM 的提示词
 */
public class CheckAnalysisPrompt {

    /** 系统角色设定 */
    public static final String SYSTEM_PROMPT = """
            你是一名资深的高校固定资产审计专家，精通资产管理政策与实务。
            你的任务是对盘点差异记录进行专业分析，判断差异成因并给出处理建议。

            分析维度参考：
            - 盘盈（实盘多于账面）：是否新购未入库？是否受赠未登记？是否调入未记账？是否是跨部门借用未归还？
            - 盘亏（实盘少于账面）：是否正常报废未销账？是否丢失或被盗？是否借出未归还？是否登记错误（重复登记/错登）？

            要求：
            1. 结合资产名称和差异数量，推断最可能的成因
            2. 分析文字控制在100字以内，简洁专业
            3. 建议文字控制在80字以内，明确可操作
            4. 严格按 JSON 数组格式返回，只返回 JSON，不要包含其他文字
            5. 每个 recordId 对应一条分析结果""";

    /**
     * 构造用户消息 Prompt
     *
     * @param task    盘点任务
     * @param records 存在差异的盘点记录（已过滤掉相符记录）
     * @return 用户消息文本
     */
    public static String build(CheckTask task, java.util.List<CheckRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下盘点任务的差异记录：\n\n");
        sb.append("【盘点背景】\n");
        sb.append("任务名称：").append(task.getTaskName()).append("\n");
        sb.append("盘点部门：").append(nullToEmpty(task.getDeptNames())).append("\n");
        sb.append("盘点期间：").append(task.getStartDate()).append(" 至 ").append(task.getEndDate()).append("\n\n");
        sb.append("【差异记录列表】\n");
        sb.append("| recordId | 资产名称 | 账面数量 | 实盘数量 | 差异 | 结果 |\n");
        sb.append("|----------|----------|----------|----------|------|------|\n");

        for (CheckRecord r : records) {
            sb.append("| ").append(r.getId())
                    .append(" | ").append(r.getAssetName())
                    .append(" | ").append(r.getBookQuantity())
                    .append(" | ").append(r.getActualQuantity())
                    .append(" | ").append(formatDiff(r.getDiffQuantity()))
                    .append(" | ").append(resultLabel(r.getResult()))
                    .append(" |\n");
        }

        sb.append("\n请对以上 ").append(records.size()).append(" 条差异记录逐一分析，返回 JSON 数组。");
        return sb.toString();
    }

    private static String formatDiff(Integer diff) {
        if (diff == null) return "0";
        return diff > 0 ? "+" + diff : String.valueOf(diff);
    }

    private static String resultLabel(String result) {
        return switch (result) {
            case "SURPLUS" -> "盘盈";
            case "SHORTAGE" -> "盘亏";
            case "MATCH" -> "相符";
            default -> result;
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
