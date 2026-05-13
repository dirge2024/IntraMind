package com.localrag.agent.model;

import java.util.Map;

public record ToolResult(
        String content,
        Map<String, Object> data,
        boolean streamedToUser
) {}
