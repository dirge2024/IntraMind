package com.localrag.guard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitProperties props;

    public void checkChatByUser(String userId) {
        long minuteSlot = System.currentTimeMillis() / 60000;
        String key = "chat:rate:user:" + userId + ":" + minuteSlot;
        check(key, props.getChatPerMinutePerUser());
    }

    public void checkChatGlobal() {
        long minuteSlot = System.currentTimeMillis() / 60000;
        String key = "chat:rate:global:" + minuteSlot;
        check(key, props.getChatPerMinuteGlobal());
    }

    public void checkLlmByUser(String userId) {
        long minuteSlot = System.currentTimeMillis() / 60000;
        String key = "llm:rate:user:" + userId + ":" + minuteSlot;
        check(key, props.getLlmPerMinutePerUser());
    }

    private void check(String key, int limit) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(60));
        }
        if (count != null && count > limit) {
            long retryAfter = 60 - (System.currentTimeMillis() / 1000) % 60;
            log.warn("rate limit exceeded: key={}, count={}, limit={}", key, count, limit);
            throw new RateLimitExceededException(
                    "请求过于频繁，请" + retryAfter + "秒后重试",
                    "user", (int) Math.max(retryAfter, 1));
        }
    }
}
