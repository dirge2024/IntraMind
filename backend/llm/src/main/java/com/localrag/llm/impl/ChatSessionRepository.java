package com.localrag.llm.impl;

import com.localrag.llm.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findAllByOrderByPinnedDescUpdatedAtDesc();
}
