package com.localrag.agent.model;

import java.time.Instant;
import java.util.Map;

public sealed interface AgentEvent {

    record Start(String generationId, String conversationId, long timestamp) implements AgentEvent {
        public Start(String generationId, String conversationId) {
            this(generationId, conversationId, System.currentTimeMillis());
        }
    }

    record Chunk(String generationId, String chunk, long timestamp) implements AgentEvent {
        public Chunk(String generationId, String chunk) {
            this(generationId, chunk, System.currentTimeMillis());
        }
    }

    record ToolCall(String generationId, String tool, String toolCallId, String status,
                    long timestamp) implements AgentEvent {
        public ToolCall(String generationId, String tool, String toolCallId, String status) {
            this(generationId, tool, toolCallId, status, System.currentTimeMillis());
        }
    }

    record Completion(String generationId, String status,
                      Map<String, Map<String, Object>> referenceMappings,
                      boolean persistenceDegraded, long timestamp) implements AgentEvent {
        public Completion(String generationId, String status,
                          Map<String, Map<String, Object>> referenceMappings,
                          boolean persistenceDegraded) {
            this(generationId, status, referenceMappings, persistenceDegraded, System.currentTimeMillis());
        }
    }

    record Stop(String generationId, long timestamp) implements AgentEvent {
        public Stop(String generationId) {
            this(generationId, System.currentTimeMillis());
        }
    }

    record Error(String generationId, int code, String message, Integer retryAfterSeconds,
                 long timestamp) implements AgentEvent {
        public Error(String generationId, int code, String message, Integer retryAfterSeconds) {
            this(generationId, code, message, retryAfterSeconds, System.currentTimeMillis());
        }
        public Error(String generationId, int code, String message) {
            this(generationId, code, message, null, System.currentTimeMillis());
        }
    }
}
