package com.localrag.controller;

import com.localrag.common.Result;
import com.localrag.llm.contract.LlmService;
import com.localrag.llm.impl.ChatHistoryManager;
import com.localrag.llm.impl.ChatSessionService;
import com.localrag.llm.model.ChatHistoryMessage;
import com.localrag.llm.model.ChatSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmService llmService;
    private final ChatHistoryManager chatHistoryManager;
    private final ChatSessionService sessionService;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam String query,
                           @RequestParam(defaultValue = "default") String sessionId) {
        try { sessionService.touchTitle(sessionId, query); } catch (Exception ignored) {}
        return llmService.chat(query, sessionId);
    }

    @GetMapping("/history")
    public Result<List<ChatHistoryMessage>> history(@RequestParam(defaultValue = "default") String sessionId) {
        return Result.ok(chatHistoryManager.getRecent(sessionId));
    }

    @GetMapping("/sessions")
    public Result<List<ChatSession>> sessions() {
        return Result.ok(sessionService.list());
    }

    @PostMapping("/sessions")
    public Result<ChatSession> createSession() {
        return Result.ok(sessionService.create());
    }

    @PutMapping("/sessions/{sessionId}")
    public Result<ChatSession> updateSession(@PathVariable String sessionId,
                                             @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        Boolean pinned = body.get("pinned") != null ? (Boolean) body.get("pinned") : null;
        return Result.ok(sessionService.update(sessionId, title, pinned));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.delete(sessionId);
        return Result.ok();
    }
}
