package com.localrag.agent.model;

import java.util.List;
import java.util.Map;

public record ReActTurn(
        String content,
        List<ToolCallDecision> toolCalls,
        Map<String, Object> assistantMessage,
        String finishReason,
        int promptTokens,
        int completionTokens
) {}
