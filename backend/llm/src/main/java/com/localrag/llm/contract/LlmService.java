package com.localrag.llm.contract;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LlmService {
    SseEmitter chat(String query, String sessionId);
}
