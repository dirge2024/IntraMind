/** LLM 对话接口：接收 query+sessionId，返回 SseEmitter 流式输出。 */
package com.localrag.llm.contract;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LlmService {
    SseEmitter chat(String query, String sessionId);
}
