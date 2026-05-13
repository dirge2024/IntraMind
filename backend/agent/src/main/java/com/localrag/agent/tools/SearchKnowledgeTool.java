package com.localrag.agent.tools;

import com.localrag.agent.contract.AgentTool;
import com.localrag.agent.model.ToolResult;
import com.localrag.retrieval.contract.RetrievalService;
import com.localrag.retrieval.model.RetrievalResult;
import com.localrag.storage.contract.FileMetadataRepository;
import com.localrag.storage.model.FileMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool implements AgentTool {

    private final RetrievalService retrievalService;
    private final FileMetadataRepository fileMetadataRepository;

    @Override
    public String name() {
        return "search_knowledge";
    }

    @Override
    public String description() {
        return "在知识库中检索与查询相关的文档片段，返回多个候选结果及来源信息。每次查询独立编号，编号从1开始。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "检索关键词或自然语言查询，保留原文中的核心名词和缩写"),
                        "topK", Map.of("type", "integer", "description", "返回结果数量，默认5，范围1-20")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args, String userId) {
        String query = (String) args.getOrDefault("query", "");
        int rawTopK = args.get("topK") instanceof Number n ? n.intValue() : 5;
        int topK = Math.max(1, Math.min(rawTopK, 20));

        List<RetrievalResult> results = retrievalService.search(query, topK);

        if (results.isEmpty()) {
            return new ToolResult("知识库中未找到相关内容，请告知用户可尝试更换关键词或确认相关知识已上传。",
                    Map.of("results", List.of()), false);
        }

        StringBuilder content = new StringBuilder();
        content.append("检索到 ").append(results.size()).append(" 条结果：\n");

        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            String fileName = resolveFileName(r.getMd5());
            content.append("[").append(i + 1).append("] ");
            if (fileName != null) {
                content.append("（").append(fileName).append("）");
            }
            content.append(" ").append(truncate(r.getText(), 300)).append("\n");
        }

        return new ToolResult(content.toString(), Map.of("results", results), false);
    }

    private String resolveFileName(String md5) {
        try {
            FileMetadata meta = fileMetadataRepository.findByMd5(md5);
            return meta != null ? meta.getFileName() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
