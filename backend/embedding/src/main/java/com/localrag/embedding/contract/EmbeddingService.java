/** 文本向量化接口：List<String> → List<float[]>。支持 Qwen/DeepSeek 双实现。 */
package com.localrag.embedding.contract;

import java.util.List;

public interface EmbeddingService {
    List<float[]> embed(List<String> texts);
}
