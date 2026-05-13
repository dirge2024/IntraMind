package com.localrag.agent.tools;

import com.localrag.agent.contract.AgentTool;
import com.localrag.agent.model.ToolResult;
import com.localrag.storage.contract.FileMetadataRepository;
import com.localrag.storage.model.FileMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KnowledgeStatsTool implements AgentTool {

    private final FileMetadataRepository fileMetadataRepository;

    @Override
    public String name() {
        return "knowledge_stats";
    }

    @Override
    public String description() {
        return "查询知识库的统计信息，包括文档总数、各状态文档数量（READY/CHUNKED/EMBEDDED/DELETED）。用于回答知识库整体情况的问题。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "detail", Map.of("type", "string", "description", "统计粒度: 'summary' 返回汇总，'full' 返回各状态明细")
                ),
                "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args, String userId) {
        try {
            List<FileMetadata> allFiles = fileMetadataRepository.findAll();
            long readyCount = allFiles.stream().filter(f -> f.getStatus() == FileMetadata.Status.READY).count();
            long chunkedCount = allFiles.stream().filter(f -> f.getStatus() == FileMetadata.Status.CHUNKED).count();
            long embeddedCount = allFiles.stream().filter(f -> f.getStatus() == FileMetadata.Status.EMBEDDED).count();
            long deletedCount = allFiles.stream().filter(f -> f.getStatus() == FileMetadata.Status.DELETED).count();

            String content = String.format("""
                            知识库统计：
                            - 文档总数：%d
                            - 已解析(READY)：%d
                            - 已分块(CHUNKED)：%d
                            - 已入库(EMBEDDED)：%d
                            - 已删除(DELETED)：%d""",
                    allFiles.size(), readyCount, chunkedCount, embeddedCount, deletedCount);

            return new ToolResult(content, Map.of(
                    "total", allFiles.size(),
                    "ready", readyCount,
                    "chunked", chunkedCount,
                    "embedded", embeddedCount,
                    "deleted", deletedCount
            ), false);
        } catch (Exception e) {
            return new ToolResult("查询知识库统计失败：" + e.getMessage(), Map.of(), false);
        }
    }
}
