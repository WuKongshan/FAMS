package com.example.asset.common.llm;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * LLM 服务 — 封装大语言模型 API 调用
 * 当前适配 DeepSeek（OpenAI 兼容接口），可扩展其他模型
 */
@Slf4j
@Service
public class LLMService {

    @Value("${llm.deepseek.api-key:}")
    private String apiKey;

    @Value("${llm.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${llm.deepseek.model:deepseek-chat}")
    private String model;

    /** 请求超时（秒） */
    private static final int TIMEOUT_SECONDS = 60;

    /**
     * 发送 Chat 请求，返回 LLM 回复中的 JSON 内容
     *
     * @param systemPrompt 系统角色提示词
     * @param userMessage  用户消息
     * @param targetType   反序列化目标类型
     * @param <T>          泛型
     * @return 解析后的对象
     */
    public <T> T chat(String systemPrompt, String userMessage, Class<T> targetType) {
        String content = rawChat(systemPrompt, userMessage);
        return JSONUtil.toBean(extractJson(content), targetType);
    }

    /**
     * 发送 Chat 请求，解析为 JSON 数组
     */
    public <T> List<T> chatForList(String systemPrompt, String userMessage, Class<T> elementType) {
        String content = rawChat(systemPrompt, userMessage);
        String json = extractJson(content);
        return JSONUtil.toList(JSONUtil.parseArray(json), elementType);
    }

    /**
     * 原始调用：发送请求并返回 LLM 文本回复
     */
    public String rawChat(String systemPrompt, String userMessage) {
        if (StrUtil.isBlank(apiKey)) {
            throw new RuntimeException("LLM API Key 未配置，请在 application.yml 中设置 llm.deepseek.api-key");
        }

        JSONObject body = JSONUtil.createObj()
                .set("model", model)
                .set("messages", JSONUtil.createArray()
                        .set(JSONUtil.createObj()
                                .set("role", "system")
                                .set("content", systemPrompt))
                        .set(JSONUtil.createObj()
                                .set("role", "user")
                                .set("content", userMessage)))
                .set("temperature", 0.3)
                .set("max_tokens", 4096);

        log.info("LLM 请求开始, model={}, promptLength={}", model, userMessage.length());
        long start = System.currentTimeMillis();

        try (HttpResponse response = HttpRequest.post(baseUrl + "/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(body))
                .timeout((int) Duration.ofSeconds(TIMEOUT_SECONDS).toMillis())
                .execute()) {

            String respBody = response.body();
            if (response.getStatus() != 200) {
                log.error("LLM API 返回异常, status={}, body={}", response.getStatus(), respBody);
                throw new RuntimeException("LLM API 调用失败: HTTP " + response.getStatus());
            }

            JSONObject json = JSONUtil.parseObj(respBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.error("LLM 返回无 choices, body={}", respBody);
                throw new RuntimeException("LLM 返回结果为空");
            }

            String content = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");

            // 记录 token 用量
            JSONObject usage = json.getJSONObject("usage");
            if (usage != null) {
                log.info("LLM 请求完成, 耗时={}ms, promptTokens={}, completionTokens={}, totalTokens={}",
                        System.currentTimeMillis() - start,
                        usage.getInt("prompt_tokens"),
                        usage.getInt("completion_tokens"),
                        usage.getInt("total_tokens"));
            }

            return content;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM 请求异常", e);
            throw new RuntimeException("LLM 服务不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 从 LLM 回复中提取 JSON 字符串
     * 兼容 ```json ... ``` 代码块包裹的情况
     */
    private String extractJson(String content) {
        String trimmed = content.trim();
        // 处理 ```json ... ``` 代码块
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n");
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }
}
