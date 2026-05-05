package com.localrag.embedding.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class EmbeddingResponse {

    @Data
    public static class QwenResponse {
        private Output output;

        @Data
        public static class Output {
            private List<Embedding> embeddings;
        }

        @Data
        public static class Embedding {
            private List<Double> embedding;
        }
    }

    @Data
    public static class OpenAiResponse {
        private List<DataItem> data;

        @Data
        public static class DataItem {
            private List<Double> embedding;
        }
    }
}
