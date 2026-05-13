package com.localrag.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "localrag.prompt")
public class PromptProperties {

    private String rules = "你是IntraMind知识库AI助手「茉莉」。\n" +
            "严格基于提供的参考资料回答用户问题。\n" +
            "如果资料不足以回答问题，明确说明「知识库暂不存在相关信息」。";

    private String refStart = "<<REF>>";
    private String refEnd = "<<END>>";
    private String noResultText = "（本轮无预置检索结果，可按需调用工具）";
}
