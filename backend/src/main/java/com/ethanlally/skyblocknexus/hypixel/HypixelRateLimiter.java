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
    private int inFlight;

    public HypixelRateLimiter() {
        this(Clock.systemUTC());
    }

    HypixelRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void acquire() {
        discardExpiredWindow();
        if (currentWindow == null) {
            inFlight++;
            return;
        }

        if (currentWindow.remaining() <= 0) {
            throw new HypixelRateLimitException(secondsUntil(currentWindow.resetsAt()));
        }

        currentWindow = new RateLimitWindow(
                currentWindow.remaining() - 1,
                currentWindow.resetsAt());
        inFlight++;
    }

    public synchronized void release() {
        inFlight--;
    }

    public synchronized void update(HttpHeaders headers) {
        Integer remaining = parseNonNegativeInt(headers.getFirst(REMAINING_HEADER));
        Long resetSeconds = parseNonNegativeLong(headers.getFirst(RESET_HEADER));
        if (remaining == null || resetSeconds == null) {
            return;
        }

        discardExpiredWindow();
        // Other requests may not yet be reflected in this response's headers.
        int available = Math.max(0, remaining - Math.max(0, inFlight - 1));
        Instant resetsAt = clock.instant().plusSeconds(resetSeconds);
        if (currentWindow != null) {
            // A late response must never refund reservations or shorten a cooldown.
            available = Math.min(available, currentWindow.remaining());
            if (currentWindow.resetsAt().isAfter(resetsAt)) {
                resetsAt = currentWindow.resetsAt();
            }
        }
        currentWindow = new RateLimitWindow(available, resetsAt);
    }

    public synchronized HypixelRateLimitException rejectedByUpstream(HttpHeaders headers) {
        discardExpiredWindow();
        update(headers);

        Long retryAfter = parseNonNegativeLong(headers.getFirst(HttpHeaders.RETRY_AFTER));
        Long resetSeconds = parseNonNegativeLong(headers.getFirst(RESET_HEADER));
        long retryAfterSeconds = retryAfter != null ? Math.max(1, retryAfter)
                : resetSeconds != null ? Math.max(1, resetSeconds) : DEFAULT_RETRY_SECONDS;
        if (currentWindow != null) {
            retryAfterSeconds = Math.max(retryAfterSeconds, secondsUntil(currentWindow.resetsAt()));
        }
        currentWindow = new RateLimitWindow(
                0,
                clock.instant().plusSeconds(retryAfterSeconds));

        return new HypixelRateLimitException(retryAfterSeconds);
    }

    private void discardExpiredWindow() {
        if (currentWindow != null && !clock.instant().isBefore(currentWindow.resetsAt())) {
            currentWindow = null;
        }
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
