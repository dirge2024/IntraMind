package com.localrag.config;

import com.localrag.common.Result;
import com.localrag.guard.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<Map<String, Object>> handleRateLimit(RateLimitExceededException e) {
        log.warn("rate limit exceeded: scope={}, retryAfter={}s", e.getScope(), e.getRetryAfterSeconds());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", e.getMessage());
        data.put("retryAfterSeconds", e.getRetryAfterSeconds());
        data.put("scope", e.getScope());
        return Result.fail(429, e.getMessage());
    }
}
