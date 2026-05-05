package com.localrag.vectorstore.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localrag.common.dto.EmbeddingRequestedPayload;
import com.localrag.messaging.model.KafkaMessage;
import com.localrag.storage.contract.FileMetadataRepository;
import com.localrag.storage.model.FileMetadata;
import com.localrag.vectorstore.contract.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreConsumer {

    private final ObjectMapper objectMapper;
    private final VectorStoreService vectorStoreService;
    private final FileMetadataRepository fileMetadataRepository;

    @KafkaListener(topics = "embedding.requested", groupId = "localrag-vector-store")
    public void onMessage(String json) {
        EmbeddingRequestedPayload payload;
        try {
            KafkaMessage<EmbeddingRequestedPayload> msg = objectMapper.readValue(
                    json, new TypeReference<KafkaMessage<EmbeddingRequestedPayload>>() {});
            payload = msg.getPayload();
        } catch (Exception e) {
            log.error("failed to deserialize embedding.requested message", e);
            return;
        }

        if (payload.getChunks() == null || payload.getChunks().isEmpty()) {
            log.warn("empty chunks for md5={}, skipped", payload.getMd5());
            return;
        }

        log.info("indexing chunks to ES: md5={}, count={}", payload.getMd5(), payload.getChunks().size());

        try {
            List<Map<String, Object>> documents = new ArrayList<>();
            for (var chunk : payload.getChunks()) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("chunkId", chunk.getChunkId());
                doc.put("md5", payload.getMd5());
                doc.put("index", chunk.getIndex());
                doc.put("text", chunk.getText());
                doc.put("charCount", chunk.getCharCount());
                doc.put("denseVector", chunk.getDenseVector());
                documents.add(doc);
            }

            vectorStoreService.indexChunks(documents);

            FileMetadata meta = fileMetadataRepository.findByMd5(payload.getMd5());
            if (meta != null) {
                meta.setStatus(FileMetadata.Status.EMBEDDED);
                fileMetadataRepository.save(meta);
            }

            log.info("indexed {} chunks to ES: md5={}", documents.size(), payload.getMd5());

        } catch (Exception e) {
            log.error("ES indexing failed for md5={}", payload.getMd5(), e);
        }
    }
}
