package com.localrag.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequestedPayload {
    private String md5;
    private List<VectorChunk> chunks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorChunk {
        private String chunkId;
        private int index;
        private String text;
        private int charCount;
        private float[] denseVector;
    }
}
