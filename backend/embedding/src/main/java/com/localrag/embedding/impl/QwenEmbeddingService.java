package com.localrag.embedding.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localrag.embedding.config.EmbeddingConfig;
import com.localrag.embedding.contract.EmbeddingService;
import com.localrag.embedding.dto.EmbeddingRequest;
import com.localrag.embedding.dto.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "localrag.embedding.provider", havingValue = "qwen", matchIfMissing = true)
public class QwenEmbeddingService implements EmbeddingService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final EmbeddingConfig config;
    private final ObjectMapper objectMapper;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public List<float[]> embed(List<String> texts) {
        EmbeddingRequest body = EmbeddingRequest.builder()
                .model(config.getQwen().getModel())
                .input(EmbeddingRequest.Input.builder().texts(texts).build())
                .parameters(EmbeddingRequest.Parameters.builder()
                        .dimension(config.getDimension()).build())
                .build();

        try {
            String json = objectMapper.writeValueAsString(body);
            Request request = new Request.Builder()
                    .url(config.getQwen().getEndpoint())
                    .header("Authorization", "Bearer " + config.getQwen().getApiKey())
                    .post(RequestBody.create(json, JSON))
                    .build();

            String responseBody;
            try (var response = client.newCall(request).execute()) {
                responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("qwen api error: status={}, body={}", response.code(), responseBody);
                    throw new RuntimeException("qwen api returned " + response.code());
                }
            }

            EmbeddingResponse.QwenResponse resp = objectMapper.readValue(
                    responseBody, EmbeddingResponse.QwenResponse.class);

            List<float[]> vectors = new ArrayList<>();
            for (var item : resp.getOutput().getEmbeddings()) {
                float[] vector = new float[item.getEmbedding().size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = item.getEmbedding().get(i).floatValue();
                }
                vectors.add(vector);
            }

            log.debug("qwen embedded {} texts → {} vectors ({}d)",
                    texts.size(), vectors.size(), vectors.isEmpty() ? 0 : vectors.get(0).length);
            return vectors;

        } catch (Exception e) {
            log.error("qwen embedding failed for {} texts", texts.size(), e);
            throw new RuntimeException("qwen embedding failed", e);
        }
    }
}
