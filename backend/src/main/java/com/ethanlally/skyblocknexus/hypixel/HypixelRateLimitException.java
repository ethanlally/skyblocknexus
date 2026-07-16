package com.ethanlally.skyblocknexus.hypixel;

public class HypixelRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    HypixelRateLimitException(long retryAfterSeconds) {
        super("Hypixel's request limit has been reached");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
