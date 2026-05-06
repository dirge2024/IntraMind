package com.localrag.controller;

import com.localrag.common.Result;
import com.localrag.llm.contract.LlmService;
import com.localrag.llm.impl.ChatHistoryManager;
import com.localrag.llm.model.ChatHistoryMessage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmService llmService;
    private final ChatHistoryManager chatHistoryManager;

    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody ChatRequest request) {
        return llmService.chat(request.getQuery(), request.getSessionId());
    }

    @GetMapping("/history")
    public Result<List<ChatHistoryMessage>> history(@RequestParam(defaultValue = "default") String sessionId) {
        return Result.ok(chatHistoryManager.getRecent(sessionId));
    }

    @Data
    public static class ChatRequest {
        private String query;
        private String sessionId = "default";
    }
}
