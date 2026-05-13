package com.localrag.agent.impl;

import com.localrag.agent.config.AgentConfig;
import com.localrag.agent.contract.AgentTool;
import com.localrag.agent.model.ToolResult;
import com.localrag.agent.tools.GenerateSummaryTool;
import com.localrag.agent.tools.KnowledgeStatsTool;
import com.localrag.agent.tools.SearchKnowledgeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final AgentConfig config;

    public AgentToolRegistry(SearchKnowledgeTool searchKnowledgeTool,
                             GenerateSummaryTool generateSummaryTool,
                             KnowledgeStatsTool knowledgeStatsTool,
                             AgentConfig config) {
        this.config = config;
        register(searchKnowledgeTool);
        register(generateSummaryTool);
        register(knowledgeStatsTool);
    }

    private void register(AgentTool tool) {
        tools.put(tool.name(), tool);
        log.info("agent tool registered: {}", tool.name());
    }

    public List<Map<String, Object>> getOpenAiTools() {
        List<Map<String, Object>> openAiTools = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parameters());

            Map<String, Object> toolSchema = new LinkedHashMap<>();
            toolSchema.put("type", "function");
            toolSchema.put("function", function);
            openAiTools.add(toolSchema);
        }
        return openAiTools;
    }

    public ToolResult executeTool(String name, Map<String, Object> args, String userId,
                                   Consumer<String> chunkConsumer) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return new ToolResult("未知工具: " + name, Map.of(), false);
        }

        log.info("executing tool: name={}, userId={}", name, userId);
        long start = System.currentTimeMillis();

        try {
            ToolResult result = tool.execute(args, userId);
            long elapsed = System.currentTimeMillis() - start;
            log.info("tool executed: name={}, elapsed={}ms, success=true", name, elapsed);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("tool execution failed: name={}, elapsed={}ms", name, elapsed, e);
            return new ToolResult("工具 " + name + " 执行失败: " + e.getMessage(), Map.of(), false);
        }
    }

    public Map<String, AgentTool> getTools() {
        return Collections.unmodifiableMap(tools);
    }
}
