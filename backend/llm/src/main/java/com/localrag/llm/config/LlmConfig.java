package com.localrag.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "localrag.llm")
public class LlmConfig {
    private String provider = "deepseek";
    private DeepSeek deepseek = new DeepSeek();

    @Data
    public static class DeepSeek {
        private String model = "deepseek-chat";
        private String apiKey;
        private String endpoint = "https://api.deepseek.com/chat/completions";
    }
}
