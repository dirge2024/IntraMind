package com.localrag.agent.model;

import java.util.Map;

public record ToolCallDecision(
        String id,
        String name,
        Map<String, Object> arguments
) {}
