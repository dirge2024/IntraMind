package com.localrag.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localrag.agent.config.AgentConfig;
import com.localrag.agent.contract.AgentLoop;
import com.localrag.agent.contract.LlmProvider;
import com.localrag.agent.model.*;
import com.localrag.llm.config.LlmConfig;
import com.localrag.llm.impl.ChatHistoryManager;
import com.localrag.llm.impl.PromptBuilder;
import com.localrag.llm.model.ChatHistoryMessage;
import com.localrag.retrieval.model.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
@Service
public class ReActLoop implements AgentLoop {

    private static final int MAX_CONTEXT_SNIPPET_LEN = 300;

    private final AgentConfig agentConfig;
    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;
    private final GenerationStateManager stateManager;
    private final ChatHistoryManager chatHistoryManager;
    private final PromptBuilder promptBuilder;
    private final LlmProvider llmProvider;
    private final ThreadPoolTaskExecutor chatExecutor;

    private final Map<String, LlmProvider.StreamHandle> activeStreams = new ConcurrentHashMap<>();
    private final Set<String> cancelledGenerations = ConcurrentHashMap.newKeySet();
    private final Map<String, CompletableFuture<Void>> pendingFutures = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, ReferenceInfo>> referenceMappings = new ConcurrentHashMap<>();

    public ReActLoop(AgentConfig agentConfig, LlmConfig llmConfig, ObjectMapper objectMapper,
                     AgentToolRegistry toolRegistry, GenerationStateManager stateManager,
                     ChatHistoryManager chatHistoryManager, PromptBuilder promptBuilder,
                     LlmProvider llmProvider) {
        this.agentConfig = agentConfig;
        this.llmConfig = llmConfig;
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        this.stateManager = stateManager;
        this.chatHistoryManager = chatHistoryManager;
        this.promptBuilder = promptBuilder;
        this.llmProvider = llmProvider;
        this.chatExecutor = new ThreadPoolTaskExecutor();
        this.chatExecutor.setCorePoolSize(4);
        this.chatExecutor.setMaxPoolSize(8);
        this.chatExecutor.setQueueCapacity(50);
        this.chatExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        this.chatExecutor.setThreadNamePrefix("agent-chat-");
        this.chatExecutor.initialize();
    }

    @Override
    public GenerationHandle chat(String userId, String query, String sessionId,
                                  Consumer<AgentEvent> eventSink) {
        String generationId = UUID.randomUUID().toString();
        String conversationId = sessionId;

        stateManager.create(generationId, userId, conversationId);
        cancelledGenerations.remove(generationId);

        eventSink.accept(new AgentEvent.Start(generationId, conversationId));

        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingFutures.put(generationId, future);

        chatExecutor.execute(() -> {
            try {
                runReActLoop(userId, query, generationId, conversationId, eventSink);
            } catch (Exception e) {
                log.error("ReAct loop failed: generationId={}", generationId, e);
                stateManager.markFailed(generationId, e.getMessage());
                eventSink.accept(new AgentEvent.Error(generationId, 500,
                        "Agent 推理异常: " + e.getMessage()));
                eventSink.accept(new AgentEvent.Completion(generationId, "failed",
                        Collections.emptyMap(), false));
            } finally {
                pendingFutures.remove(generationId);
                activeStreams.remove(generationId);
                if (future != null && !future.isDone()) {
                    future.complete(null);
                }
            }
        });

        return new GenerationHandle(generationId, future.isDone());
    }

    private void runReActLoop(String userId, String query, String generationId,
                               String conversationId, Consumer<AgentEvent> eventSink) {
        List<Map<String, Object>> messages = buildReActMessages(query, conversationId);
        int executedToolCalls = 0;
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;

        for (int round = 1; round <= agentConfig.getMaxRounds(); round++) {
            if (isCancelled(generationId)) {
                finalizeCancelled(generationId, conversationId, eventSink);
                return;
            }

            boolean lastRound = round == agentConfig.getMaxRounds() ||
                    executedToolCalls >= agentConfig.getMaxToolCalls();
            List<Map<String, Object>> tools = lastRound
                    ? List.of()
                    : toolRegistry.getOpenAiTools();

            ReActTurn turn = streamReActTurn(userId, generationId, messages, tools, eventSink);
            if (turn == null) {
                return; // 流被取消
            }

            totalPromptTokens += turn.promptTokens();
            totalCompletionTokens += turn.completionTokens();

            if (turn.toolCalls().isEmpty()) {
                finalizeResponse(userId, query, generationId, conversationId,
                        turn, eventSink, totalPromptTokens, totalCompletionTokens);
                return;
            }

            messages.add(turn.assistantMessage());
            for (ToolCallDecision tc : turn.toolCalls()) {
                if (isCancelled(generationId)) {
                    finalizeCancelled(generationId, conversationId, eventSink);
                    return;
                }

                eventSink.accept(new AgentEvent.ToolCall(generationId, tc.name(), tc.id(), "executing"));

                if (executedToolCalls >= agentConfig.getMaxToolCalls()) {
                    String exhaustedMsg = "工具调用预算已用尽（上限" + agentConfig.getMaxToolCalls() + "次），本次工具未执行。请基于已有结果给出最终回答。";
                    messages.add(toolMessage(tc.id(), exhaustedMsg));
                    eventSink.accept(new AgentEvent.ToolCall(generationId, tc.name(), tc.id(), "failed"));
                    continue;
                }

                ToolResult result;
                try {
                    result = toolRegistry.executeTool(tc.name(), tc.arguments(), userId, null);
                    executedToolCalls++;
                    eventSink.accept(new AgentEvent.ToolCall(generationId, tc.name(), tc.id(), "success"));
                } catch (Exception e) {
                    result = new ToolResult("工具执行异常: " + e.getMessage(), Map.of(), false);
                    eventSink.accept(new AgentEvent.ToolCall(generationId, tc.name(), tc.id(), "failed"));
                }

                if ("search_knowledge".equals(tc.name())) {
                    buildReferenceMapping(generationId, result.data());
                }

                messages.add(toolMessage(tc.id(),
                        result.content() != null ? result.content() : "工具执行完成，无返回内容。"));

                if (result.streamedToUser()) {
                    finalizeResponse(userId, query, generationId, conversationId,
                            turn, eventSink, totalPromptTokens, totalCompletionTokens);
                    return;
                }
            }
        }

        messages.add(Map.of("role", "user", "content",
                "ReAct 轮次已用尽，请不要再调用工具，直接基于已有结果给出最终回答。"));
        ReActTurn finalTurn = streamReActTurn(userId, generationId, messages, List.of(), eventSink);
        if (finalTurn != null) {
            finalizeResponse(userId, query, generationId, conversationId,
                    finalTurn, eventSink, totalPromptTokens, totalCompletionTokens);
        }
    }

    private ReActTurn streamReActTurn(String userId, String generationId,
                                       List<Map<String, Object>> messages,
                                       List<Map<String, Object>> tools,
                                       Consumer<AgentEvent> eventSink) {
        CompletableFuture<ReActTurn> turnFuture = new CompletableFuture<>();
        StreamAccumulator acc = new StreamAccumulator(objectMapper, 512);

        LlmProvider.StreamHandle handle = llmProvider.streamChat(
                userId,
                messages,
                tools,
                agentConfig.getMaxCompletionTokens(),
                chunk -> {
                    acc.consume(chunk);
                    String delta = acc.getContentDelta();
                    if (delta != null && !delta.isEmpty() && !isCancelled(generationId)) {
                        stateManager.appendChunk(generationId, delta);
                        eventSink.accept(new AgentEvent.Chunk(generationId, delta));
                    }
                },
                error -> {
                    log.error("LLM stream error: generationId={}", generationId, error);
                    turnFuture.completeExceptionally(error);
                },
                () -> {
                    ReActTurn turn = acc.toTurn();
                    turnFuture.complete(turn);
                }
        );

        if (handle == null) {
            eventSink.accept(new AgentEvent.Error(generationId, 500, "LLM 调用失败"));
            return null;
        }

        activeStreams.put(generationId, handle);

        long deadline = System.nanoTime() +
                TimeUnit.SECONDS.toNanos(agentConfig.getCompletionTimeoutSeconds());
        try {
            while (true) {
                if (isCancelled(generationId)) {
                    handle.cancel();
                    return null;
                }
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    handle.cancel();
                    log.warn("LLM response timeout: generationId={}", generationId);
                    stateManager.markFailed(generationId, "模型响应超时");
                    eventSink.accept(new AgentEvent.Error(generationId, 504,
                            "模型响应超时，请稍后重试", null));
                    return null;
                }
                try {
                    long waitMs = Math.min(TimeUnit.NANOSECONDS.toMillis(remainingNanos), 200L);
                    return turnFuture.get(Math.max(waitMs, 1L), TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handle.cancel();
            return null;
        } catch (ExecutionException e) {
            handle.cancel();
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.error("LLM stream failed: generationId={}", generationId, cause);
            stateManager.markFailed(generationId, cause.getMessage());
            eventSink.accept(new AgentEvent.Error(generationId, 500,
                    "LLM 调用失败: " + cause.getMessage(), null));
            return null;
        } finally {
            activeStreams.remove(generationId, handle);
        }
    }

    private List<Map<String, Object>> buildMessagesWithTools(
            List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        // tools are passed to LLM API via the request body; this is handled by the LlmProvider
        return messages;
    }

    @Override
    public void stop(String generationId) {
        if (generationId == null || generationId.isBlank()) {
            return;
        }
        cancelledGenerations.add(generationId);
        stateManager.markCancelled(generationId);

        LlmProvider.StreamHandle handle = activeStreams.get(generationId);
        if (handle != null) {
            handle.cancel();
        }

        log.info("generation stopped: generationId={}", generationId);
    }

    private void finalizeResponse(String userId, String query, String generationId,
                                   String conversationId, ReActTurn turn,
                                   Consumer<AgentEvent> eventSink,
                                   int totalPromptTokens, int totalCompletionTokens) {
        if (isCancelled(generationId)) {
            finalizeCancelled(generationId, conversationId, eventSink);
            return;
        }

        stateManager.markCompleted(generationId);
        Map<String, Map<String, Object>> refMappings = stateManager.getReferences(generationId);

        boolean persisted = persistConversation(userId, query, turn.content(), conversationId, refMappings);
        if (persisted) {
            chatHistoryManager.append(conversationId, "user", query);
            chatHistoryManager.append(conversationId, "assistant", turn.content());
        }

        log.info("ReAct complete: generationId={}, rounds={}, promptTokens={}, completionTokens={}, contentChars={}",
                generationId, "?", totalPromptTokens, totalCompletionTokens, turn.content().length());

        eventSink.accept(new AgentEvent.Completion(generationId, "finished", refMappings, !persisted));
    }

    private void finalizeCancelled(String generationId, String conversationId,
                                    Consumer<AgentEvent> eventSink) {
        eventSink.accept(new AgentEvent.Stop(generationId));
        eventSink.accept(new AgentEvent.Completion(generationId, "cancelled",
                Collections.emptyMap(), false));
        referenceMappings.remove(generationId);
    }

    private boolean persistConversation(String userId, String userMessage, String response,
                                         String conversationId,
                                         Map<String, Map<String, Object>> refMappings) {
        try {
            chatHistoryManager.append(conversationId, "user", userMessage);
            chatHistoryManager.append(conversationId, "assistant", response);
            return true;
        } catch (Exception e) {
            log.error("persist conversation failed: userId={}, conversationId={}", userId, conversationId, e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void buildReferenceMapping(String generationId, Map<String, Object> toolData) {
        if (toolData == null) return;
        Object resultsObj = toolData.get("results");
        if (!(resultsObj instanceof List<?> rawList) || rawList.isEmpty()) return;

        Map<Integer, ReferenceInfo> mapping = new HashMap<>();
        int refNum = 1;
        for (Object item : rawList) {
            if (!(item instanceof RetrievalResult r) || r.getMd5() == null) continue;
            mapping.put(refNum, new ReferenceInfo(
                    r.getMd5(), resolveFileName(r.getMd5()), null, null, null,
                    r.getScore(), refNum));
            refNum++;
        }
        if (!mapping.isEmpty()) {
            referenceMappings.put(generationId, mapping);
            stateManager.updateReferences(generationId, mapping);
        }
    }

    private String resolveFileName(String md5) {
        return md5 != null ? md5.substring(0, Math.min(8, md5.length())) : "unknown";
    }

    private List<Map<String, Object>> buildReActMessages(String query, String conversationId) {
        List<Map<String, Object>> messages = new ArrayList<>();

        StringBuilder sysBuilder = new StringBuilder();
        sysBuilder.append("""
                你是IntraMind知识库AI助手「茉莉」。
                本系统是知识库优先的问答助手：你的首要职责是基于本系统已收录的资料回答用户。
                默认必须先调用 search_knowledge，再基于检索结果作答。

                强制检索原则（默认行为）：
                1. 默认调用 search_knowledge：只要问题涉及任何实体、名称、缩写、产品、项目、术语、流程、功能、实现、背景、对比、引用，无论你是否自认为已知答案，都必须先检索。
                2. 构造 query 时严格保留用户原话中的核心名词、缩写和限定词，禁止替换为泛化关键词。
                3. 用户要求整理、总结、归纳时，先用 search_knowledge 圈定材料，再调用 generate_summary。

                可以跳过检索的白名单：
                - 纯打招呼或寒暄（你好/谢谢/再见等）
                - 纯翻译请求，且不涉及本系统术语
                - 与本系统材料无关的纯创作请求
                - 通用编程语法、数学计算等不依赖专有信息的常识题

                回答与异常处理：
                - 只要 search_knowledge 返回了片段，必须基于片段作答并标注来源编号
                - 只有工具明确返回零片段时，才说明暂无相关材料
                - 工具失败时根据错误信息决定下一步（重试/换query/继续推理）
                """);

        List<ChatHistoryMessage> history = chatHistoryManager.getRecent(conversationId);
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - agentConfig.getHistoryMaxMessages());
            for (ChatHistoryMessage msg : history.subList(start, history.size())) {
                String role = msg.getRole();
                if (!"user".equals(role) && !"assistant".equals(role)) continue;
                String content = msg.getContent();
                if (content.length() > agentConfig.getHistoryMaxContentChars()) {
                    content = content.substring(0, agentConfig.getHistoryMaxContentChars()) + "...";
                }
                messages.add(Map.of("role", role, "content", content));
            }
        }

        messages.add(0, Map.of("role", "system", "content", sysBuilder.toString()));
        messages.add(Map.of("role", "user", "content", query));

        return messages;
    }

    private Map<String, Object> toolMessage(String toolCallId, String content) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", toolCallId != null ? toolCallId : "");
        msg.put("content", content != null ? content : "");
        return msg;
    }

    private boolean isCancelled(String generationId) {
        return generationId != null && cancelledGenerations.contains(generationId);
    }

}
