/** DeepSeek Chat API SSE 实现：检索→拼 Prompt→调 API→逐字流式返回，完成后写聊天记录。 */
package com.localrag.llm.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localrag.llm.config.LlmConfig;
import com.localrag.llm.contract.LlmService;
import com.localrag.retrieval.contract.RetrievalService;
import com.localrag.retrieval.model.RetrievalResult;
import com.localrag.storage.contract.FileMetadataRepository;
import com.localrag.storage.model.FileMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekLlmService implements LlmService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final LlmConfig config;
    private final ObjectMapper objectMapper;
    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final ChatHistoryManager chatHistoryManager;
    private final FileMetadataRepository fileMetadataRepository;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public SseEmitter chat(String query, String sessionId) {
        SseEmitter emitter = new SseEmitter(120000L);

        executor.submit(() -> {
            try {
                List<RetrievalResult> chunks = retrievalService.search(query, 5);
                List<com.localrag.llm.model.ChatHistoryMessage> history =
                        chatHistoryManager.getRecent(sessionId);

                java.util.HashMap<String, String> md5ToFileName = new java.util.HashMap<>();
                for (var chunk : chunks) {
                    if (!md5ToFileName.containsKey(chunk.getMd5())) {
                        FileMetadata meta = fileMetadataRepository.findByMd5(chunk.getMd5());
                        md5ToFileName.put(chunk.getMd5(),
                                meta != null ? meta.getFileName() : chunk.getMd5().substring(0, 8));
                    }
                }

                String prompt = promptBuilder.build(query, chunks, history, md5ToFileName);

                if (chunks.isEmpty()) {
                    emitter.send(SseEmitter.event().data(
                            Map.of("content", "未找到相关信息。请先上传相关文档到知识库。", "done", true)));
                    emitter.complete();
                    return;
                }

                Map<String, Object> body = Map.of(
                        "model", config.getDeepseek().getModel(),
                        "messages", List.of(
                                Map.of("role", "user", "content", prompt)
                        ),
                        "stream", true
                );

                String json = objectMapper.writeValueAsString(body);
                Request request = new Request.Builder()
                        .url(config.getDeepseek().getEndpoint())
                        .header("Authorization", "Bearer " + config.getDeepseek().getApiKey())
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(json, JSON))
                        .build();

                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "";
                        log.error("deepseek api error: status={}, body={}", response.code(), errBody);
                        emitter.send(SseEmitter.event().data(
                                Map.of("content", "API 调用失败: " + response.code(), "done", true)));
                        emitter.complete();
                        return;
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(),
                                    java.nio.charset.StandardCharsets.UTF_8));
                    String line;
                    StringBuilder fullAnswer = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) break;

                            try {
                                JsonNode node = objectMapper.readTree(data);
                                JsonNode choices = node.get("choices");
                                if (choices != null && choices.size() > 0) {
                                    JsonNode delta = choices.get(0).get("delta");
                                    if (delta != null) {
                                        JsonNode content = delta.get("content");
                                        if (content != null && !content.asText().isEmpty()) {
                                            String text = content.asText();
                                            fullAnswer.append(text);
                                            emitter.send(SseEmitter.event().data(
                                                    Map.of("content", text, "done", false)));
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    chatHistoryManager.append(sessionId, "user", query);
                    chatHistoryManager.append(sessionId, "assistant", fullAnswer.toString());

                    emitter.send(SseEmitter.event().data(
                            Map.of("content", "", "done", true)));
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("llm streaming failed", e);
                try {
                    emitter.send(SseEmitter.event().data(
                            Map.of("content", "服务异常: " + e.getMessage(), "done", true)));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }
}
