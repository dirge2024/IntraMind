package com.localrag.llm.impl;

import com.localrag.llm.model.ChatHistoryMessage;
import com.localrag.retrieval.model.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    public String build(String query, List<RetrievalResult> chunks, List<ChatHistoryMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 LocalRAG 知识库助手。只根据【参考资料】回答，不要编造。\n\n");

        if (!chunks.isEmpty()) {
            sb.append("【参考资料】\n");
            for (var chunk : chunks) {
                sb.append(chunk.getText()).append("\n\n");
            }
        }

        if (!history.isEmpty()) {
            sb.append("【历史对话】\n");
            for (var msg : history) {
                sb.append(msg.getRole()).append("：").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("【用户问题】\n").append(query);
        return sb.toString();
    }
}
