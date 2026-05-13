package com.localrag.agent.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localrag.agent.config.AgentConfig;
import com.localrag.agent.model.ReferenceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationStateManager {

    private static final String KEY_PREFIX = "chat:generation:";

    private final RedisTemplate<String, String> redisTemplate;
    private final AgentConfig config;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, StringBuilder> localContentCache = new ConcurrentHashMap<>();

    public void create(String generationId, String userId, String sessionId) {
        String metaKey = KEY_PREFIX + generationId + ":meta";
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("userId", userId);
        meta.put("conversationId", sessionId);
        meta.put("status", "STREAMING");
        meta.put("createdAt", Instant.now().toString());
        redisTemplate.opsForHash().putAll(metaKey, meta);
        redisTemplate.expire(metaKey, Duration.ofMinutes(config.getTtlMinutes()));
        localContentCache.put(generationId, new StringBuilder());
    }

    public void appendChunk(String generationId, String chunk) {
        StringBuilder sb = localContentCache.get(generationId);
        if (sb != null) {
            sb.append(chunk);
        }
        redisTemplate.opsForValue().append(KEY_PREFIX + generationId + ":content", chunk);
        redisTemplate.expire(KEY_PREFIX + generationId + ":content", Duration.ofMinutes(config.getTtlMinutes()));
    }

    public void updateReferences(String generationId, Map<Integer, ReferenceInfo> refs) {
        try {
            Map<String, Map<String, Object>> serializable = new LinkedHashMap<>();
            for (Map.Entry<Integer, ReferenceInfo> e : refs.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fileMd5", e.getValue().fileMd5());
                item.put("fileName", e.getValue().fileName());
                item.put("pageNumber", e.getValue().pageNumber());
                item.put("score", e.getValue().score());
                item.put("chunkId", e.getValue().chunkId());
                serializable.put(String.valueOf(e.getKey()), item);
            }
            String json = objectMapper.writeValueAsString(serializable);
            redisTemplate.opsForValue().set(KEY_PREFIX + generationId + ":refs", json,
                    Duration.ofMinutes(config.getTtlMinutes()));
        } catch (Exception e) {
            log.warn("update references failed: generationId={}", generationId, e);
        }
    }

    public Map<String, Map<String, Object>> getReferences(String generationId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + generationId + ":refs");
            if (json == null || json.isEmpty()) {
                return Collections.emptyMap();
            }
            return objectMapper.readValue(json,
                    new TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("read references failed: generationId={}", generationId, e);
            return Collections.emptyMap();
        }
    }

    public void markCompleted(String generationId) {
        setStatus(generationId, "COMPLETED");
    }

    public void markFailed(String generationId, String error) {
        setStatus(generationId, "FAILED");
        redisTemplate.opsForHash().put(KEY_PREFIX + generationId + ":meta", "error", error);
    }

    public void markCancelled(String generationId) {
        setStatus(generationId, "CANCELLED");
    }

    public boolean isCancelled(String generationId) {
        String status = getStatus(generationId);
        return "CANCELLED".equals(status);
    }

    private void setStatus(String generationId, String status) {
        redisTemplate.opsForHash().put(KEY_PREFIX + generationId + ":meta", "status", status);
        if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
            localContentCache.remove(generationId);
        }
    }

    private String getStatus(String generationId) {
        Object value = redisTemplate.opsForHash().get(KEY_PREFIX + generationId + ":meta", "status");
        return value != null ? value.toString() : null;
    }
}
