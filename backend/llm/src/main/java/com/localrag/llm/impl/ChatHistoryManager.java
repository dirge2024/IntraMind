package com.localrag.llm.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localrag.llm.model.ChatHistoryMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHistoryManager {

    private static final String PREFIX = "chat:";
    private static final Duration TTL = Duration.ofHours(24);
    private static final int MAX_RECENT = 15;

    private final RedisTemplate<String, String> redisTemplate;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ObjectMapper objectMapper;

    public void append(String sessionId, String role, String content) {
        ChatHistoryMessage msg = ChatHistoryMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();

        chatHistoryRepository.save(msg);

        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "role", role, "content", content,
                    "time", msg.getCreatedAt().toString()
            ));
            String key = PREFIX + sessionId;
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("failed to cache chat history to Redis for session={}", sessionId, e);
        }
    }

    public List<ChatHistoryMessage> getRecent(String sessionId) {
        String key = PREFIX + sessionId;
        List<String> cached = redisTemplate.opsForList().range(key, -MAX_RECENT, -1);

        if (cached != null && cached.size() >= MAX_RECENT) {
            return parseFromRedis(cached, sessionId);
        }

        List<ChatHistoryMessage> fromDb = chatHistoryRepository
                .findTop15BySessionIdOrderByCreatedAtDesc(sessionId);

        if (fromDb.isEmpty()) {
            return parseFromRedis(cached != null ? cached : List.of(), sessionId);
        }

        List<ChatHistoryMessage> result = new ArrayList<>(fromDb);
        java.util.Collections.reverse(result);

        if (cached != null) {
            for (String json : cached) {
                ChatHistoryMessage msg = parseOne(json, sessionId);
                if (msg != null) {
                    boolean exists = result.stream()
                            .anyMatch(r -> r.getCreatedAt().equals(msg.getCreatedAt()));
                    if (!exists) {
                        result.add(msg);
                    }
                }
            }
        }

        if (result.size() > MAX_RECENT) {
            result = result.subList(result.size() - MAX_RECENT, result.size());
        }
        return result;
    }

    private List<ChatHistoryMessage> parseFromRedis(List<String> items, String sessionId) {
        List<ChatHistoryMessage> result = new ArrayList<>();
        for (String json : items) {
            ChatHistoryMessage msg = parseOne(json, sessionId);
            if (msg != null) result.add(msg);
        }
        return result;
    }

    private ChatHistoryMessage parseOne(String json, String sessionId) {
        try {
            Map<String, String> map = objectMapper.readValue(json, Map.class);
            return ChatHistoryMessage.builder()
                    .sessionId(sessionId)
                    .role(map.get("role"))
                    .content(map.get("content"))
                    .createdAt(LocalDateTime.parse(map.get("time")))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
