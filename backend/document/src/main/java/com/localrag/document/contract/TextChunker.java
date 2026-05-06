/** 文本分块接口：将长文本按段落/句子边界切分为 Chunk 列表，支持重叠。 */
package com.localrag.document.contract;

import com.localrag.document.model.Chunk;

import java.util.List;

public interface TextChunker {
    List<Chunk> chunk(String text, int bufferSize, int minChunk, double overlapPct);
}
