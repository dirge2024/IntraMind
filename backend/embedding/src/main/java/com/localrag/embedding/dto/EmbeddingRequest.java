package com.localrag.embedding.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EmbeddingRequest {
    private String model;
    private Input input;
    private Parameters parameters;

    @Data
    @Builder
    public static class Input {
        private List<String> texts;
    }

    @Data
    @Builder
    public static class Parameters {
        private int dimension;
    }
}
