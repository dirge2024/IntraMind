package com.localrag.llm.impl;

import com.localrag.llm.model.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository sessionRepo;
    private final ChatHistoryRepository historyRepo;

    public ChatSession create() {
        ChatSession session = ChatSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .title("新对话")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return sessionRepo.save(session);
    }

    public List<ChatSession> list() {
        return sessionRepo.findAllByOrderByPinnedDescUpdatedAtDesc();
    }

    public ChatSession update(String sessionId, String title, Boolean pinned) {
        ChatSession session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null) return null;
        if (title != null) session.setTitle(title);
        if (pinned != null) session.setPinned(pinned);
        session.setUpdatedAt(LocalDateTime.now());
        return sessionRepo.save(session);
    }

    public void delete(String sessionId) {
        historyRepo.deleteBySessionId(sessionId);
        sessionRepo.deleteById(sessionId);
    }

    public void touchTitle(String sessionId, String firstQuery) {
        ChatSession session = sessionRepo.findById(sessionId).orElse(null);
        if (session != null && "新对话".equals(session.getTitle())) {
            String title = firstQuery.length() > 20 ? firstQuery.substring(0, 20) : firstQuery;
            session.setTitle(title);
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepo.save(session);
        } else if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepo.save(session);
        }
    }
}
