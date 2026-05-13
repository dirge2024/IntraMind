package com.localrag.config;

import com.localrag.agent.contract.LlmProvider;
import com.localrag.llm.config.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class DeepSeekLlmProvider implements LlmProvider {

    private final LlmConfig llmConfig;
    private final WebClient client;

    public DeepSeekLlmProvider(LlmConfig llmConfig) {
        this.llmConfig = llmConfig;
        this.client = WebClient.builder()
                .baseUrl(llmConfig.getDeepseek().getEndpoint())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + llmConfig.getDeepseek().getApiKey())
                .build();
    }

    @Override
    public StreamHandle streamChat(String userId,
                                   List<Map<String, Object>> messages,
                                   List<Map<String, Object>> tools,
                                   int maxTokens,
                                   Consumer<String> onChunk,
                                   Consumer<Throwable> onError,
                                   Runnable onComplete) {
        Map<String, Object> request = buildRequest(messages, tools, maxTokens);

        var subscription = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .subscribe(onChunk, onError, onComplete);

        return new StreamHandle(subscription, () -> {});
    }

    @Override
    public String getName() {
        return "deepseek";
    }

    private Map<String, Object> buildRequest(List<Map<String, Object>> messages,
                                              List<Map<String, Object>> tools,
                                              int maxTokens) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", llmConfig.getDeepseek().getModel());
        request.put("messages", messages);
        request.put("stream", true);
        request.put("stream_options", Map.of("include_usage", true));
        request.put("max_tokens", maxTokens > 0 ? maxTokens : 2000);
        request.put("temperature", 0.3);
        request.put("top_p", 0.9);

        if (tools != null && !tools.isEmpty()) {
            request.put("tools", tools);
            request.put("tool_choice", "auto");
        }
        return request;
    }
}
