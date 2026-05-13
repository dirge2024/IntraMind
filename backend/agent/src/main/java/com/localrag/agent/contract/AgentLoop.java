package com.localrag.agent.contract;

import com.localrag.agent.model.AgentEvent;

import java.util.function.Consumer;

public interface AgentLoop {

    GenerationHandle chat(String userId, String query, String sessionId,
                          Consumer<AgentEvent> eventSink);

    void stop(String generationId);

    record GenerationHandle(String generationId, boolean completed) {}
}
