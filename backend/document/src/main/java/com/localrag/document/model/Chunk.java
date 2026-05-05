package com.localrag.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {
    private String chunkId;
    private int index;
    private String text;
    private int charCount;
}
