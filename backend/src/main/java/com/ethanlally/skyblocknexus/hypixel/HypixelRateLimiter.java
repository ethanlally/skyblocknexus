package com.ethanlally.skyblocknexus.hypixel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class HypixelRateLimiter {

    private static final String REMAINING_HEADER = "RateLimit-Remaining";
    private static final String RESET_HEADER = "RateLimit-Reset";
    private static final long DEFAULT_RETRY_SECONDS = 60;

    private final Clock clock;
    private RateLimitWindow currentWindow;

    public HypixelRateLimiter() {
        this(Clock.systemUTC());
    }

    HypixelRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void acquire() {
        if (currentWindow == null) {
            return;
        }

        Instant now = clock.instant();
        if (!now.isBefore(currentWindow.resetsAt())) {
            currentWindow = null;
            return;
        }

        if (currentWindow.remaining() <= 0) {
            throw new HypixelRateLimitException(secondsUntil(currentWindow.resetsAt()));
        }

        currentWindow = new RateLimitWindow(
                currentWindow.remaining() - 1,
                currentWindow.resetsAt());
    }

    public synchronized void update(HttpHeaders headers) {
        Integer remaining = parseNonNegativeInt(headers.getFirst(REMAINING_HEADER));
        Long resetSeconds = parseNonNegativeLong(headers.getFirst(RESET_HEADER));
        if (remaining == null || resetSeconds == null) {
            return;
        }

        currentWindow = new RateLimitWindow(
                remaining,
                clock.instant().plusSeconds(resetSeconds));
    }

    public synchronized HypixelRateLimitException rejectedByUpstream(HttpHeaders headers) {
        update(headers);

        long retryAfterSeconds = currentWindow == null
                ? retryAfterFallback(headers)
                : secondsUntil(currentWindow.resetsAt());
        currentWindow = new RateLimitWindow(
                0,
                clock.instant().plusSeconds(retryAfterSeconds));

        return new HypixelRateLimitException(retryAfterSeconds);
    }

    private long retryAfterFallback(HttpHeaders headers) {
        Long retryAfter = parseNonNegativeLong(headers.getFirst(HttpHeaders.RETRY_AFTER));
        return retryAfter == null ? DEFAULT_RETRY_SECONDS : Math.max(1, retryAfter);
    }

    private long secondsUntil(Instant resetAt) {
        long milliseconds = Math.max(0, Duration.between(clock.instant(), resetAt).toMillis());
        return Math.max(1, (milliseconds + 999) / 1000);
    }

    private Integer parseNonNegativeInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException | NullPointerException ignored) {
            return null;
        }
    }

    private Long parseNonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException | NullPointerException ignored) {
            return null;
        }
    }

    private record RateLimitWindow(int remaining, Instant resetsAt) {}
}
