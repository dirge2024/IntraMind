package com.localrag.vectorstore.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.localrag.vectorstore.contract.VectorStoreService;
import com.localrag.vectorstore.exception.VectorStoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private static final String INDEX = "localrag-chunks";

    private final ElasticsearchClient client;

    @Override
    public void indexChunks(List<Map<String, Object>> documents) {
        try {
            List<BulkOperation> ops = documents.stream()
                    .map(doc -> BulkOperation.of(b -> b
                            .index(idx -> idx
                                    .index(INDEX)
                                    .id((String) doc.get("chunkId"))
                                    .document(doc))))
                    .toList();

            var response = client.bulk(BulkRequest.of(b -> b.operations(ops)));

            if (response.errors()) {
                long failed = response.items().stream()
                        .filter(item -> item.error() != null)
                        .count();
                log.error("bulk index had {} failures out of {}", failed, documents.size());
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .forEach(item -> log.error("bulk error: id={}, reason={}",
                                item.id(), item.error().reason()));
            }

            log.info("indexed {} chunks to ES", documents.size() - response.items().stream()
                    .filter(item -> item.error() != null).count());

        } catch (Exception e) {
            log.error("ES bulk index failed for {} documents", documents.size(), e);
            throw new VectorStoreException("ES 批量写入失败");
        }
    }

    @Override
    public void deleteByMd5(String md5) {
        try {
            var response = client.deleteByQuery(d -> d
                    .index(INDEX)
                    .query(q -> q.term(t -> t.field("md5").value(md5))));
            log.info("ES deleted {} chunks for md5={}", response.deleted(), md5);
        } catch (Exception e) {
            log.error("ES delete failed for md5={}", md5, e);
        }
    }

    @Override
    public void createIndex(String indexName) {
        try {
            client.indices().create(CreateIndexRequest.of(b -> b
                    .index(indexName)
                    .mappings(m -> m
                            .properties("chunkId", Property.of(p -> p.keyword(k -> k)))
                            .properties("md5", Property.of(p -> p.keyword(k -> k)))
                            .properties("index", Property.of(p -> p.integer(i -> i)))
                            .properties("text", Property.of(p -> p
                                    .text(t -> t.analyzer("ik_max_word"))))
                            .properties("charCount", Property.of(p -> p.integer(i -> i)))
                            .properties("denseVector", Property.of(p -> p
                                    .denseVector(v -> v.dims(2048).index(true)
                                            .similarity("cosine"))))
                    )));
            log.info("ES index created: {}", indexName);
        } catch (Exception e) {
            log.error("failed to create ES index: {}", indexName, e);
            throw new VectorStoreException("ES 索引创建失败");
        }
    }

    @Override
    public boolean indexExists(String indexName) {
        try {
            return client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        } catch (Exception e) {
            log.error("ES index exists check failed", e);
            return false;
        }
    }
}
