package com.localrag.guard;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "localrag.rate-limit")
public class RateLimitProperties {

    private int chatPerMinutePerUser = 10;
    private int chatPerMinuteGlobal = 100;
    private int llmPerMinutePerUser = 5;
    private int llmDailyTokensPerUser = 100_000;
    private int llmDailyTokensGlobal = 5_000_000;
}
