package com.localrag.embedding.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localrag.common.dto.DocumentChunkedPayload;
import com.localrag.common.dto.EmbeddingRequestedPayload;
import com.localrag.embedding.config.EmbeddingConfig;
import com.localrag.embedding.contract.EmbeddingService;
import com.localrag.messaging.contract.MessageProducer;
import com.localrag.messaging.model.KafkaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingConsumer {

    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;
    private final MessageProducer messageProducer;
    private final EmbeddingConfig config;

    @KafkaListener(topics = "document.chunked", groupId = "localrag-embedding")
    public void onMessage(String json) {
        DocumentChunkedPayload payload;
        try {
            KafkaMessage<DocumentChunkedPayload> msg = objectMapper.readValue(
                    json, new TypeReference<KafkaMessage<DocumentChunkedPayload>>() {});
            payload = msg.getPayload();
        } catch (Exception e) {
            log.error("failed to deserialize document.chunked message", e);
            return;
        }

        if (payload.getChunks() == null || payload.getChunks().isEmpty()) {
            log.warn("empty chunks for md5={}, skipped", payload.getMd5());
            return;
        }

        log.info("processing embedding: md5={}, chunks={}", payload.getMd5(), payload.getChunks().size());

        int batchSize = config.getBatchSize();
        List<DocumentChunkedPayload.ChunkData> allChunks = payload.getChunks();
        List<EmbeddingRequestedPayload.VectorChunk> vectorChunks = new ArrayList<>();

        for (int i = 0; i < allChunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allChunks.size());
            List<DocumentChunkedPayload.ChunkData> batch = allChunks.subList(i, end);

            List<String> texts = batch.stream()
                    .map(c -> truncate(c.getText()))
                    .collect(Collectors.toList());

            List<float[]> vectors = embedWithRetry(texts);

            for (int j = 0; j < batch.size(); j++) {
                DocumentChunkedPayload.ChunkData chunk = batch.get(j);
                vectorChunks.add(EmbeddingRequestedPayload.VectorChunk.builder()
                        .chunkId(chunk.getChunkId())
                        .index(chunk.getIndex())
                        .text(chunk.getText())
                        .charCount(chunk.getCharCount())
                        .denseVector(vectors.get(j))
                        .build());
            }

            log.debug("batch {}/{} embedded: {} texts → {} vectors",
                    (i / batchSize) + 1, (int) Math.ceil((double) allChunks.size() / batchSize),
                    texts.size(), vectors.size());
        }

        int outBatchSize = 20;
        for (int i = 0; i < vectorChunks.size(); i += outBatchSize) {
            int end = Math.min(i + outBatchSize, vectorChunks.size());
            messageProducer.send("embedding.requested", EmbeddingRequestedPayload.builder()
                    .md5(payload.getMd5())
                    .chunks(vectorChunks.subList(i, end))
                    .build());
        }

        log.info("embedding done: md5={}, chunks={}", payload.getMd5(), vectorChunks.size());
    }

    private List<float[]> embedWithRetry(List<String> texts) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return embeddingService.embed(texts);
            } catch (Exception e) {
                if (attempt == 3) {
                    log.error("embedding failed after 3 attempts for {} texts", texts.size(), e);
                    throw new RuntimeException("embedding failed after retries", e);
                }
                long delay = (long) Math.pow(2, attempt - 1) * 1000;
                log.warn("embedding attempt {}/3 failed, retrying in {}ms", attempt, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new RuntimeException("unreachable");
    }

    private String truncate(String text) {
        if (text.length() > 8000) {
            return text.substring(0, 8000);
        }
        return text;
    }
}
