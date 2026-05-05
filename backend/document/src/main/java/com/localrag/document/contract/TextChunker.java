package com.localrag.document.contract;

import com.localrag.document.model.Chunk;

import java.util.List;

public interface TextChunker {
    List<Chunk> chunk(String text, int bufferSize, int minChunk, double overlapPct);
}
