package com.localrag.agent.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localrag.agent.model.ReActTurn;
import com.localrag.agent.model.ToolCallDecision;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
class StreamAccumulator {

    private final ObjectMapper objectMapper;
    final StringBuilder content = new StringBuilder();
    final StringBuilder reasoningContent = new StringBuilder();
    final Map<Integer, StreamingToolCall> toolCalls = new LinkedHashMap<>();
    String finishReason;
    int promptTokens;
    int completionTokens;
    int estimatedPromptTokens;
    boolean settled;
    private String contentDelta;

    StreamAccumulator(ObjectMapper objectMapper, int estimatedPromptTokens) {
        this.objectMapper = objectMapper;
        this.estimatedPromptTokens = estimatedPromptTokens;
    }

    String getContentDelta() {
        String delta = contentDelta;
        contentDelta = null;
        return delta;
    }

    void consume(String rawChunk) {
        try {
            for (String chunk : extractPayloads(rawChunk)) {
                if ("[DONE]".equals(chunk)) {
                    continue;
                }

                JsonNode node = objectMapper.readTree(chunk);

                JsonNode usage = node.path("usage");
                if (usage.isObject()) {
                    promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                    completionTokens = usage.path("completion_tokens").asInt(completionTokens);
                }

                JsonNode choice = node.path("choices").path(0);
                if (!choice.isObject()) {
                    continue;
                }

                JsonNode frNode = choice.path("finish_reason");
                if (!frNode.isMissingNode() && !frNode.isNull()) {
                    String fr = frNode.asText("");
                    if (!fr.isBlank()) {
                        finishReason = fr;
                    }
                }

                JsonNode delta = choice.path("delta");

                String rc = delta.path("reasoning_content").asText("");
                if (!rc.isEmpty()) {
                    reasoningContent.append(rc);
                }

                String text = delta.path("content").asText("");
                if (!text.isEmpty()) {
                    content.append(text);
                    contentDelta = (contentDelta == null ? "" : contentDelta) + text;
                }

                JsonNode tcArray = delta.path("tool_calls");
                if (tcArray.isArray()) {
                    for (JsonNode tcDelta : tcArray) {
                        appendToolCallDelta(tcDelta);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("process stream chunk failed: {}", e.getMessage());
        }
    }

    private void appendToolCallDelta(JsonNode delta) {
        int index = delta.path("index").asInt(toolCalls.size());
        StreamingToolCall stc = toolCalls.computeIfAbsent(index, i -> new StreamingToolCall());
        String id = delta.path("id").asText("");
        if (!id.isBlank()) {
            stc.id = id;
        }
        String type = delta.path("type").asText("");
        if (!type.isBlank()) {
            stc.type = type;
        }
        JsonNode func = delta.path("function");
        if (func.isObject()) {
            String name = func.path("name").asText("");
            if (!name.isBlank()) {
                stc.name.append(name);
            }
            String args = func.path("arguments").asText("");
            if (!args.isEmpty()) {
                stc.arguments.append(args);
            }
        }
    }

    Map<String, Object> assistantMessage() {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        List<Map<String, Object>> serializedToolCalls = serializedToolCalls();
        if (!serializedToolCalls.isEmpty()) {
            String text = content.toString();
            msg.put("content", text.isBlank() ? null : text);
            msg.put("tool_calls", serializedToolCalls);
        } else {
            msg.put("content", content.toString());
        }
        if (!reasoningContent.isEmpty()) {
            msg.put("reasoning_content", reasoningContent.toString());
        }
        return msg;
    }

    private List<Map<String, Object>> serializedToolCalls() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Integer, StreamingToolCall> e : toolCalls.entrySet()) {
            StreamingToolCall stc = e.getValue();
            if (stc.name.isEmpty()) {
                continue;
            }
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", stc.name.toString());
            func.put("arguments", stc.arguments.isEmpty() ? "{}" : stc.arguments.toString());

            Map<String, Object> call = new LinkedHashMap<>();
            call.put("id", stc.id == null || stc.id.isBlank() ? "call_" + e.getKey() : stc.id);
            call.put("type", stc.type == null || stc.type.isBlank() ? "function" : stc.type);
            call.put("function", func);
            list.add(call);
        }
        return list;
    }

    ReActTurn toTurn() {
        List<ToolCallDecision> decisions = new ArrayList<>();
        for (Map<String, Object> item : serializedToolCalls()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> func = (Map<String, Object>) item.get("function");
            String argsJson = String.valueOf(func.getOrDefault("arguments", "{}"));
            Map<String, Object> args;
            try {
                args = new ObjectMapper().readValue(argsJson.isBlank() ? "{}" : argsJson,
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                args = Map.of();
            }
            decisions.add(new ToolCallDecision(
                    String.valueOf(item.getOrDefault("id", "")),
                    String.valueOf(func.getOrDefault("name", "")),
                    args));
        }
        return new ReActTurn(
                content.toString().trim(),
                decisions,
                assistantMessage(),
                finishReason == null || finishReason.isBlank() ? "unknown" : finishReason,
                promptTokens > 0 ? promptTokens : estimatedPromptTokens,
                completionTokens > 0 ? completionTokens : 128);
    }

    private List<String> extractPayloads(String rawChunk) {
        List<String> payloads = new ArrayList<>();
        if (rawChunk == null || rawChunk.isBlank()) {
            return payloads;
        }
        String trimmed = rawChunk.trim();
        for (String line : trimmed.split("\\r?\\n")) {
            String payload = line.trim();
            if (payload.isEmpty() || payload.startsWith(":")) {
                continue;
            }
            if (payload.startsWith("data:")) {
                payload = payload.substring(5).trim();
            }
            if (!payload.isEmpty()) {
                payloads.add(payload);
            }
        }
        if (payloads.isEmpty()) {
            payloads.add(trimmed);
        }
        return payloads;
    }

    private static final class StreamingToolCall {
        String id;
        String type;
        final StringBuilder name = new StringBuilder();
        final StringBuilder arguments = new StringBuilder();
    }
}
