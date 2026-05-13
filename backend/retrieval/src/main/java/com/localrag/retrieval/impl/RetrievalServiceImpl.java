/** ES 混合检索实现：第一轮 KNN 向量召回 Top30，第二轮 BM25 重排，第三轮 rescore 精排。 */
package com.localrag.retrieval.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.localrag.embedding.contract.EmbeddingService;
import com.localrag.retrieval.contract.RetrievalService;
import com.localrag.retrieval.model.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final ElasticsearchClient client;
    private final EmbeddingService embeddingService;

    @Override
    public List<RetrievalResult> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            List<Float> queryVector;
            try {
                float[] vector = embeddingService.embed(List.of(query)).get(0);
                queryVector = new ArrayList<>();
                for (float v : vector) {
                    queryVector.add(v);
                }
            } catch (Exception e) {
                log.warn("embedding failed, falling back to BM25-only: {}", e.getMessage());
                return textOnlySearch(query, topK);
            }

            // 第一轮：KNN 向量召回 Top 30
            SearchResponse<Map> knnResp = client.search(s -> s
                    .index("localrag-chunks")
                    .knn(k -> k
                            .field("denseVector")
                            .queryVector(queryVector)
                            .k(30)
                            .numCandidates(100))
                    .size(30), Map.class);

            List<String> knnIds = new ArrayList<>();
            for (var hit : knnResp.hits().hits()) {
                Map<String, Object> src = (Map<String, Object>) hit.source();
                if (src != null && src.get("chunkId") != null) {
                    knnIds.add(src.get("chunkId").toString());
                }
            }

            if (knnIds.isEmpty()) {
                log.info("KNN returned no results for query: {}", query);
                return List.of();
            }
            log.debug("KNN recalled {} candidates", knnIds.size());

            // 第二轮：BM25 在 KNN 的 30 条内重排
            SearchResponse<Map> bm25Resp = client.search(s -> s
                    .index("localrag-chunks")
                    .query(q -> q.bool(b -> b
                            .must(m -> m.ids(i -> i.values(knnIds)))
                            .must(m -> m.match(mm -> mm.field("text").query(query)))
                    ))
                    .size(15), Map.class);

            List<String> bm25Ids = new ArrayList<>();
            for (var hit : bm25Resp.hits().hits()) {
                Map<String, Object> src = (Map<String, Object>) hit.source();
                if (src != null && src.get("chunkId") != null) {
                    bm25Ids.add(src.get("chunkId").toString());
                }
            }

            if (bm25Ids.isEmpty()) {
                return mapResults(knnResp, knnIds);
            }
            log.debug("BM25 narrowed to {} candidates", bm25Ids.size());

            // 第三轮：rescore 精排 → Top K
            SearchResponse<Map> finalResp = client.search(s -> s
                    .index("localrag-chunks")
                    .query(q -> q.ids(i -> i.values(bm25Ids)))
                    .rescore(r -> r
                            .windowSize(bm25Ids.size())
                            .query(rq -> rq
                                    .query(rqq -> rqq.match(m -> m
                                            .field("text").query(query)))
                                    .queryWeight(0.2)
                                    .rescoreQueryWeight(0.8)
                            ))
                    .size(Math.min(topK, bm25Ids.size())), Map.class);

            return mapResults(finalResp, bm25Ids);

        } catch (Exception e) {
            log.error("retrieval failed for query: {}", query, e);
            throw new RuntimeException("检索失败", e);
        }
    }

    private List<RetrievalResult> mapResults(SearchResponse<Map> response, List<String> idOrder) {
        List<RetrievalResult> results = new ArrayList<>();
        for (var hit : response.hits().hits()) {
            Map<String, Object> src = (Map<String, Object>) hit.source();
            if (src == null) continue;
            results.add(RetrievalResult.builder()
                    .chunkId(toString(src.get("chunkId")))
                    .md5(toString(src.get("md5")))
                    .text(toString(src.get("text")))
                    .charCount(toInt(src.get("charCount")))
                    .score(hit.score() != null ? hit.score() : 0.0)
                    .build());
        }
        return results;
    }

    private List<RetrievalResult> textOnlySearch(String query, int topK) {
        try {
            SearchResponse<Map> bm25Resp = client.search(s -> s
                    .index("localrag-chunks")
                    .query(q -> q.match(m -> m.field("text").query(query)))
                    .size(topK), Map.class);
            List<RetrievalResult> results = new ArrayList<>();
            for (var hit : bm25Resp.hits().hits()) {
                Map<String, Object> src = (Map<String, Object>) hit.source();
                if (src == null) continue;
                results.add(RetrievalResult.builder()
                        .chunkId(toString(src.get("chunkId")))
                        .md5(toString(src.get("md5")))
                        .text(toString(src.get("text")))
                        .charCount(toInt(src.get("charCount")))
                        .score(hit.score() != null ? hit.score() : 0.0)
                        .build());
            }
            log.info("BM25-only fallback returned {} results for query: {}", results.size(), query);
            return results;
        } catch (Exception e) {
            log.error("BM25-only search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String toString(Object o) { return o != null ? o.toString() : ""; }
    private int toInt(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }
}
