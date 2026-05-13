package com.localrag.agent.tools;

import com.localrag.agent.contract.AgentTool;
import com.localrag.agent.model.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateSummaryTool implements AgentTool {

    @Override
    public String name() {
        return "generate_summary";
    }

    @Override
    public String description() {
        return "对检索到的多条内容片段进行归纳总结，生成精简摘要。适用于用户要求整理、总结、提炼知识库内容的场景。需先调用 search_knowledge 获取素材。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "topic", Map.of("type", "string", "description", "需要总结的主题或问题"),
                        "referenceNumbers", Map.of(
                                "type", "array",
                                "items", Map.of("type", "integer"),
                                "description", "需要归纳的引用编号列表，对应 search_knowledge 返回的 [N] 编号"
                        )
                ),
                "required", List.of("topic")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args, String userId) {
        String topic = (String) args.getOrDefault("topic", "");
        @SuppressWarnings("unchecked")
        List<Integer> referenceNumbers = (List<Integer>) args.get("referenceNumbers");

        String prompt;
        if (referenceNumbers != null && !referenceNumbers.isEmpty()) {
            prompt = "请基于之前检索到的引用编号 " + referenceNumbers + " 对应的内容，对以下主题进行归纳总结：" + topic;
        } else {
            prompt = "请基于已有的检索结果，对以下主题进行归纳总结：" + topic;
        }

        return new ToolResult(prompt, Map.of("topic", topic), true);
    }
}
