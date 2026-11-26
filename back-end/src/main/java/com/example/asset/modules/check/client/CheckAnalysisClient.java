package com.example.asset.modules.check.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.asset.modules.check.dto.LLMAnalysisItem;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 盘点差异分析服务（Python）的客户端。
 *
 * <p>Java 侧不再拼 prompt、不再解析模型输出、不再重试：分片（20 条/片）、
 * 每片 2 次重试与降级都在 Python 侧完成，这里只发一次 HTTP 请求。
 *
 * <p>失败语义：非 200、响应缺 items、IO 异常都直接抛 {@link RuntimeException}，
 * 由调用方（{@code CheckServiceImpl#analyzeDiff}）决定是否降级为模板分析，本类不吞异常。
 */
@Slf4j
@Component
public class CheckAnalysisClient {

    @Value("${analysis.base-url:http://127.0.0.1:8000}")
    private String baseUrl;

    @Value("${analysis.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    /**
     * 读取超时必须是分钟级：Python 侧单片最长 60s、最多 2 次重试 + 1s/2s 退避，最坏约 183s。
     * 按 60s 断开会在对方还在重试时判失败，白白丢掉本可成功的分析。
     */
    @Value("${analysis.read-timeout-ms:200000}")
    private int readTimeoutMs;

    @Value("${analysis.enabled:true}")
    private boolean enabled;

    /** 返回 items 映射与 engine 标识；engine 仅用于日志，不对外暴露 */
    public record AnalysisOutcome(Map<Long, LLMAnalysisItem> items, String engine) {}

    /**
     * 把一批差异记录发给分析服务。
     *
     * @param task    盘点任务（可为 null，缺字段按空串传）
     * @param records 需要分析的差异记录
     * @return recordId 到分析结果的映射，以及 engine 标识
     */
    public AnalysisOutcome analyze(CheckTask task, List<CheckRecord> records) {
        if (!enabled) {
            // 交给 Service 的既有降级分支处理，这里不另写模板逻辑
            throw new IllegalStateException("analysis service disabled");
        }

        String url = StrUtil.removeSuffix(baseUrl, "/") + "/analyze/check-diff";
        String responseBody;

        try (HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .setConnectionTimeout(connectTimeoutMs)
                .setReadTimeout(readTimeoutMs)
                .body(buildRequestBody(task, records))
                .execute()) {

            responseBody = response.body();
            if (response.getStatus() != 200) {
                throw new RuntimeException("分析服务返回 HTTP " + response.getStatus()
                        + ": " + StrUtil.maxLength(responseBody, 300));
            }
        }

        JSONObject json = JSONUtil.parseObj(responseBody);
        JSONArray items = json.getJSONArray("items");
        if (items == null) {
            throw new RuntimeException("分析服务响应缺少 items: " + StrUtil.maxLength(responseBody, 300));
        }

        Map<Long, LLMAnalysisItem> itemMap = new HashMap<>(items.size());
        for (Object element : items) {
            if (!(element instanceof JSONObject itemJson)) {
                continue;
            }
            LLMAnalysisItem item = JSONUtil.toBean(itemJson, LLMAnalysisItem.class);
            if (item.getRecordId() != null) {
                itemMap.put(item.getRecordId(), item);
            }
        }

        Object engineValue = json.get("engine");
        String engine = engineValue == null ? null : engineValue.toString();
        log.info("分析服务调用完成, 差异条数: {}, 返回条数: {}, 引擎: {}", records.size(), itemMap.size(), engine);
        return new AnalysisOutcome(itemMap, engine);
    }

    /**
     * 构造请求体。字段名与 Python 侧契约逐字一致，改名会导致校验失败。
     */
    private String buildRequestBody(CheckTask task, List<CheckRecord> records) {
        JSONObject taskJson = JSONUtil.createObj()
                .set("taskName", task == null ? "" : nullToEmpty(task.getTaskName()))
                .set("deptNames", task == null ? "" : nullToEmpty(task.getDeptNames()))
                .set("startDate", task == null ? "" : dateText(task.getStartDate()))
                .set("endDate", task == null ? "" : dateText(task.getEndDate()));

        JSONArray recordArray = JSONUtil.createArray();
        for (CheckRecord record : records) {
            recordArray.add(JSONUtil.createObj()
                    .set("recordId", record.getId())
                    .set("assetName", record.getAssetName())
                    .set("assetCode", record.getAssetCode())
                    .set("bookQuantity", record.getBookQuantity())
                    .set("actualQuantity", record.getActualQuantity())
                    .set("diffQuantity", record.getDiffQuantity())
                    .set("result", record.getResult()));
        }

        return JSONUtil.createObj()
                .set("task", taskJson)
                .set("records", recordArray)
                .toString();
    }

    /** 日期按 yyyy-MM-dd 传字符串；null 传空串，避免把 "null" 写进 prompt */
    private static String dateText(LocalDate date) {
        return date == null ? "" : String.valueOf(date);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
