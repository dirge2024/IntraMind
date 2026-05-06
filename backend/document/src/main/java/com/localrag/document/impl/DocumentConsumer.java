/** Kafka 消费者：监听 document.uploaded，下载文件→Tika解析→分块→发送 document.chunked（每批20条）。 */
package com.localrag.document.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localrag.common.dto.DocumentChunkedPayload;
import com.localrag.common.dto.FileUploadedPayload;
import com.localrag.document.contract.DocumentParser;
import com.localrag.document.contract.TextChunker;
import com.localrag.document.model.Chunk;
import com.localrag.messaging.contract.MessageProducer;
import com.localrag.messaging.model.KafkaMessage;
import com.localrag.storage.config.MinioConfig;
import com.localrag.storage.contract.FileMetadataRepository;
import com.localrag.storage.contract.StorageService;
import com.localrag.storage.model.FileMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentConsumer {

    private static final int CHUNK_SIZE = 512;
    private static final double OVERLAP_PCT = 0.15;

    private final ObjectMapper objectMapper;
    private final StorageService storageService;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final MessageProducer messageProducer;
    private final MinioConfig minioConfig;
    private final FileMetadataRepository fileMetadataRepository;

    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    @KafkaListener(topics = "document.uploaded", groupId = "localrag-document")
    public void onMessage(String json) {
        FileUploadedPayload payload;
        try {
            KafkaMessage<FileUploadedPayload> msg = objectMapper.readValue(
                    json, new TypeReference<KafkaMessage<FileUploadedPayload>>() {});
            payload = msg.getPayload();
        } catch (Exception e) {
            log.error("failed to deserialize message", e);
            return;
        }

        if (processed.contains(payload.getMd5())) {
            log.info("duplicate message, skipped: md5={}", payload.getMd5());
            return;
        }

        log.info("processing document: md5={}, fileName={}", payload.getMd5(), payload.getFileName());

        try {
            // 链路: 下载 → Tika解析 → 分块 → 分批发送 document.chunked
            String presignedUrl = storageService.getPresignedUrl(
                    minioConfig.getBucket(), payload.getObjectKey(), 300);

            HttpURLConnection conn = (HttpURLConnection) new URI(presignedUrl).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            try (InputStream stream = conn.getInputStream()) {
                String text = documentParser.parse(stream);
                if (text.isEmpty()) {
                    log.warn("empty text after parsing: md5={}", payload.getMd5());
                    processed.add(payload.getMd5());
                    return;
                }

                List<Chunk> chunks = textChunker.chunk(text, CHUNK_SIZE, 0, OVERLAP_PCT);
                log.info("chunked: md5={}, chunks={}", payload.getMd5(), chunks.size());

                List<DocumentChunkedPayload.ChunkData> allChunkData = chunks.stream()
                        .map(c -> DocumentChunkedPayload.ChunkData.builder()
                                .chunkId(payload.getMd5() + "_" + String.format("%04d", c.getIndex()))
                                .index(c.getIndex())
                                .text(c.getText())
                                .charCount(c.getCharCount())
                                .build())
                        .collect(Collectors.toList());

                int batchSize = 20;
                for (int i = 0; i < allChunkData.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, allChunkData.size());
                    messageProducer.send("document.chunked", DocumentChunkedPayload.builder()
                            .md5(payload.getMd5())
                            .fileName(payload.getFileName())
                            .chunks(allChunkData.subList(i, end))
                            .build());
                }
                log.info("sent {} document.chunked messages ({} chunks total)",
                        (int) Math.ceil((double) allChunkData.size() / batchSize), allChunkData.size());

                processed.add(payload.getMd5());

                FileMetadata meta = fileMetadataRepository.findByMd5(payload.getMd5());
                if (meta != null) {
                    meta.setStatus(FileMetadata.Status.CHUNKED);
                    fileMetadataRepository.save(meta);
                }

                log.info("document processed: md5={}, fileName={}, chunks={}",
                        payload.getMd5(), payload.getFileName(), chunks.size());
            }
        } catch (Exception e) {
            log.error("document processing failed: md5={}, fileName={}",
                    payload.getMd5(), payload.getFileName(), e);
        }
    }
}
