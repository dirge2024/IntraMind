package com.localrag.embedding.contract;

import java.util.List;

public interface EmbeddingService {
    List<float[]> embed(List<String> texts);
}
