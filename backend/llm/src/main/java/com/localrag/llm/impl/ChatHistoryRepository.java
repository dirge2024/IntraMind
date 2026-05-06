package com.localrag.llm.impl;

import com.localrag.llm.model.ChatHistoryMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistoryMessage, Long> {
    List<ChatHistoryMessage> findTop15BySessionIdOrderByCreatedAtDesc(String sessionId);
}
