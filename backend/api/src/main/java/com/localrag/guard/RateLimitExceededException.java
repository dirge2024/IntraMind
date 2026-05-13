package com.localrag.guard;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;
    private final String scope;

    public RateLimitExceededException(String message, String scope, int retryAfterSeconds) {
        super(message);
        this.scope = scope;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitExceededException(String message, int retryAfterSeconds) {
        this(message, "user", retryAfterSeconds);
    }
}
