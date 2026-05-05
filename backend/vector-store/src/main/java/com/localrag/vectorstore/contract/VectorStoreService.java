package com.localrag.vectorstore.contract;

import java.util.List;
import java.util.Map;

public interface VectorStoreService {
    void indexChunks(List<Map<String, Object>> documents);
    void createIndex(String indexName);
    boolean indexExists(String indexName);
}
