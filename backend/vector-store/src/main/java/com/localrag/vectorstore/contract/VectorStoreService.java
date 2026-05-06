/** ES 向量存储接口：indexChunks（bulk 写入）、deleteByMd5（删除）、索引管理。 */
package com.localrag.vectorstore.contract;

import java.util.List;
import java.util.Map;

public interface VectorStoreService {
    void indexChunks(List<Map<String, Object>> documents);
    void deleteByMd5(String md5);
    void createIndex(String indexName);
    boolean indexExists(String indexName);
}
