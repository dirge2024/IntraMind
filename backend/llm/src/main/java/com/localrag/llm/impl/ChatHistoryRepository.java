package com.localrag.llm.impl;

import com.localrag.llm.model.ChatHistoryMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistoryMessage, Long> {
    List<ChatHistoryMessage> findTop15BySessionIdOrderByCreatedAtDesc(String sessionId);

    @Transactional
    void deleteBySessionId(String sessionId);
}
