/** LLM 对话 API：GET /api/llm/chat(SSE流式) + history + sessions(CRUD) + stop + feedback。 */
package com.localrag.controller;

import com.localrag.agent.contract.AgentLoop;
import com.localrag.agent.model.AgentEvent;
import com.localrag.common.Result;
import com.localrag.guard.RateLimitService;
import com.localrag.llm.contract.LlmService;
import com.localrag.llm.impl.ChatHistoryManager;
import com.localrag.llm.impl.ChatSessionService;
import com.localrag.llm.model.ChatHistoryMessage;
import com.localrag.llm.model.ChatSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmService llmService;
    private final AgentLoop agentLoop;
    private final ChatHistoryManager chatHistoryManager;
    private final ChatSessionService sessionService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam String query,
                           @RequestParam(defaultValue = "default") String sessionId,
                           @RequestParam(defaultValue = "false") boolean agent,
                           jakarta.servlet.http.HttpServletRequest request) {
        String userId = resolveUserId(request);
        rateLimitService.checkChatByUser(userId);
        rateLimitService.checkChatGlobal();
        rateLimitService.checkLlmByUser(userId);

        try { sessionService.touchTitle(sessionId, query); } catch (Exception ignored) {}

        if (!agent) {
            return llmService.chat(query, sessionId);
        }

        SseEmitter emitter = new SseEmitter(180000L);

        agentLoop.chat("anonymous", query, sessionId, event -> {
            try {
                String json = serializeEvent(event);
                emitter.send(SseEmitter.event()
                        .name(resolveEventName(event))
                        .data(json));
            } catch (Exception e) {
                log.debug("SSE send failed for generationId={}", extractGenerationId(event), e);
            }
        });

        emitter.onCompletion(() -> {});
        emitter.onTimeout(() -> {});
        emitter.onError(e -> log.warn("SSE emitter error", e));

        return emitter;
    }

    @PostMapping("/stop")
    public Result<Map<String, Object>> stopGeneration(@RequestBody(required = false) StopRequest body) {
        String generationId = body != null ? body.getGenerationId() : null;
        agentLoop.stop(generationId);
        return Result.ok(Map.of("generationId", generationId != null ? generationId : "",
                "status", "cancelled"));
    }

    @PostMapping("/feedback")
    public Result<Void> submitFeedback(@RequestBody FeedbackRequest body) {
        log.info("feedback: userId={}, generationId={}, rating={}, comment={}",
                body.getUserId(), body.getGenerationId(), body.getRating(), body.getComment());
        return Result.ok();
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

    private String resolveEventName(AgentEvent event) {
        if (event instanceof AgentEvent.Start) return "start";
        if (event instanceof AgentEvent.Chunk) return "chunk";
        if (event instanceof AgentEvent.ToolCall) return "tool_call";
        if (event instanceof AgentEvent.Completion) return "completion";
        if (event instanceof AgentEvent.Stop) return "stop";
        if (event instanceof AgentEvent.Error) return "error";
        return "unknown";
    }

    private String serializeEvent(AgentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractGenerationId(AgentEvent event) {
        if (event instanceof AgentEvent.Start) return ((AgentEvent.Start) event).generationId();
        if (event instanceof AgentEvent.Chunk) return ((AgentEvent.Chunk) event).generationId();
        if (event instanceof AgentEvent.ToolCall) return ((AgentEvent.ToolCall) event).generationId();
        if (event instanceof AgentEvent.Completion) return ((AgentEvent.Completion) event).generationId();
        if (event instanceof AgentEvent.Stop) return ((AgentEvent.Stop) event).generationId();
        if (event instanceof AgentEvent.Error) return ((AgentEvent.Error) event).generationId();
        return "";
    }

    private String resolveUserId(jakarta.servlet.http.HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? userId.toString() : "anonymous";
    }

    @Data
    public static class StopRequest {
        private String generationId;
    }

    @Data
    public static class FeedbackRequest {
        private String userId;
        private String generationId;
        private String rating;
        private String comment;
    }
}
