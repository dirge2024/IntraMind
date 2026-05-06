package com.localrag.controller;

import com.localrag.llm.contract.LlmService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmService llmService;

    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody ChatRequest request) {
        return llmService.chat(request.getQuery(), request.getSessionId());
    }

    @Data
    public static class ChatRequest {
        private String query;
        private String sessionId = "default";
    }
}
