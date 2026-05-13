package com.localrag.agent.contract;

import java.util.Map;

public interface AgentTool {

    String name();

    String description();

    Map<String, Object> parameters();

    com.localrag.agent.model.ToolResult execute(Map<String, Object> args, String userId);
}
