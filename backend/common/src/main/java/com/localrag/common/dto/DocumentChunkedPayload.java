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
public class DocumentChunkedPayload {
    private String md5;
    private String fileName;
    private List<ChunkData> chunks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkData {
        private String chunkId;
        private int index;
        private String text;
        private int charCount;
    }
}
