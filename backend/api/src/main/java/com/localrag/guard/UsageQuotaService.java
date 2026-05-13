package com.localrag.guard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageQuotaService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitProperties props;

    public record TokenReservation(String userId, int estimated, int maxCompletion, long timestamp) {}

    public TokenReservation reserveLlmUsage(String userId, int estimatedPrompt, int maxCompletion) {
        int estimated = estimatedPrompt + maxCompletion;
        String today = LocalDate.now().toString();
        String userKey = "llm:usage:user:" + userId + ":" + today;
        String globalKey = "llm:usage:global:" + today;

        Long userTotal = redisTemplate.opsForValue().increment(userKey, estimated);
        Long globalTotal = redisTemplate.opsForValue().increment(globalKey, estimated);

        long secondsToMidnight = ChronoUnit.SECONDS.between(
                LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay());
        redisTemplate.expire(userKey, Duration.ofSeconds(secondsToMidnight));
        redisTemplate.expire(globalKey, Duration.ofSeconds(secondsToMidnight));

        if (userTotal != null && userTotal > props.getLlmDailyTokensPerUser()) {
            redisTemplate.opsForValue().decrement(userKey, estimated);
            redisTemplate.opsForValue().decrement(globalKey, estimated);
            log.warn("user daily token quota exceeded: userId={}, total={}, limit={}",
                    userId, userTotal, props.getLlmDailyTokensPerUser());
            throw new RuntimeException("今日 Token 额度已用完，请明天再试");
        }
        if (globalTotal != null && globalTotal > props.getLlmDailyTokensGlobal()) {
            redisTemplate.opsForValue().decrement(userKey, estimated);
            redisTemplate.opsForValue().decrement(globalKey, estimated);
            log.warn("global daily token quota exceeded: total={}, limit={}",
                    globalTotal, props.getLlmDailyTokensGlobal());
            throw new RuntimeException("系统 Token 额度已用完");
        }

        return new TokenReservation(userId, estimated, maxCompletion, System.currentTimeMillis());
    }

    public void settleReservation(TokenReservation r, int actualTokens) {
        String today = LocalDate.now().toString();
        int diff = r.estimated() - actualTokens;
        if (diff > 0) {
            redisTemplate.opsForValue().decrement("llm:usage:user:" + r.userId() + ":" + today, diff);
            redisTemplate.opsForValue().decrement("llm:usage:global:" + today, diff);
        }
    }

    public void abortReservation(TokenReservation r) {
        String today = LocalDate.now().toString();
        redisTemplate.opsForValue().decrement("llm:usage:user:" + r.userId() + ":" + today, r.estimated());
        redisTemplate.opsForValue().decrement("llm:usage:global:" + today, r.estimated());
    }

    public int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int asciiChars = 0;
        int cjkChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || c >= 0x4E00 && c <= 0x9FFF) {
                cjkChars++;
            } else if (c < 128) {
                asciiChars++;
            } else {
                cjkChars++;
            }
        }
        return (int) Math.ceil(asciiChars * 0.3 + cjkChars * 0.95);
    }

    public int estimateChatTokens(List<Map<String, String>> messages) {
        int tokens = 0;
        for (Map<String, String> msg : messages) {
            tokens += 8;
            tokens += estimateTextTokens(msg.getOrDefault("role", ""));
            tokens += estimateTextTokens(msg.getOrDefault("content", ""));
        }
        return Math.max(tokens, 1);
    }
}
