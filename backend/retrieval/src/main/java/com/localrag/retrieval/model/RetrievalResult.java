package com.localrag.retrieval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResult {
    private String chunkId;
    private String md5;
    private String text;
    private int charCount;
    private double score;
}
