package com.localrag.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "localrag.agent")
public class AgentConfig {

    private int maxRounds = 4;
    private int maxToolCalls = 8;
    private int completionTimeoutSeconds = 120;
    private int maxCompletionTokens = 2000;
    private int historyMaxMessages = 6;
    private int historyMaxContentChars = 800;
    private int maxContextSnippetLen = 300;
    private int ttlMinutes = 30;
}
