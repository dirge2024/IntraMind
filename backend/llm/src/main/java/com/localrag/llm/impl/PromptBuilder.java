/** RAG Prompt 拼装：系统提示词 + 参考资料(含来源标注) + 历史对话(近15条) + 当前问题。 */
package com.localrag.llm.impl;

import com.localrag.llm.model.ChatHistoryMessage;
import com.localrag.retrieval.model.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PromptBuilder {

    public String build(String query, List<RetrievalResult> chunks,
                        List<ChatHistoryMessage> history, Map<String, String> md5ToFileName) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「茉莉」，LocalRAG 知识库助手。严格遵守以下规则：\n");
        sb.append("1. 只根据【参考资料】回答。如果参考资料中找不到与问题相关的内容，只回复「知识库中不存在相关信息」，不要标注来源\n");
        sb.append("2. 不要编造、猜测、补充任何外部知识\n");
        sb.append("3. 如果参考资料中有相关内容，回答末尾必须标注引用来源，格式：(来源N: 文件名)\n\n");

        if (!chunks.isEmpty()) {
            sb.append("【参考资料】\n");
            int idx = 1;
            for (var chunk : chunks) {
                String fileName = md5ToFileName.getOrDefault(chunk.getMd5(), "未知文件");
                sb.append("[来源").append(idx).append(": ").append(fileName).append("]\n");
                sb.append(chunk.getText()).append("\n\n");
                idx++;
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
