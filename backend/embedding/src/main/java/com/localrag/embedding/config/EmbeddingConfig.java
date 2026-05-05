package com.localrag.embedding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "localrag.embedding")
public class EmbeddingConfig {
    private String provider = "qwen";
    private int batchSize = 25;
    private int dimension = 2048;
    private int connectTimeout = 10;
    private int readTimeout = 30;

    private Qwen qwen = new Qwen();
    private DeepSeek deepseek = new DeepSeek();

    @Data
    public static class Qwen {
        private String model = "text-embedding-v3";
        private String apiKey;
        private String endpoint = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
    }

    @Data
    public static class DeepSeek {
        private String model = "deepseek-embedding";
        private String apiKey;
        private String endpoint = "https://api.deepseek.com/v1/embeddings";
    }
}
