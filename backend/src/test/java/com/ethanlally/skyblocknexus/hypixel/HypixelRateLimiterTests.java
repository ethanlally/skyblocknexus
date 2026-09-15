package com.ethanlally.skyblocknexus.hypixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class HypixelRateLimiterTests {

    private final Instant now = Instant.parse("2026-09-12T12:00:00Z");
    private final HypixelRateLimiter limiter = new HypixelRateLimiter(Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void lateResponsesDoNotRefundInFlightReservations() {
        limiter.update(headers(2, 30));
        limiter.acquire();
        limiter.acquire();
        limiter.update(headers(1, 30));
        limiter.release();

        assertThatThrownBy(limiter::acquire).isInstanceOf(HypixelRateLimitException.class);
        limiter.update(headers(0, 30));
        limiter.release();
        assertThatThrownBy(limiter::acquire).isInstanceOf(HypixelRateLimitException.class);
    }

    @Test
    void firstResponseAccountsForOtherRequestsAlreadyInFlight() {
        limiter.acquire();
        limiter.acquire();
        limiter.update(headers(1, 30));
        limiter.release();

        assertThatThrownBy(limiter::acquire).isInstanceOf(HypixelRateLimitException.class);
        limiter.release();
    }

    @Test
    void lateSuccessCannotClearOrShortenACooldown() {
        limiter.acquire();
        limiter.acquire();
        HttpHeaders rejected = headers(0, 20);
        rejected.set(HttpHeaders.RETRY_AFTER, "60");
        assertThat(limiter.rejectedByUpstream(rejected).getRetryAfterSeconds()).isEqualTo(60);
        limiter.release();
        limiter.update(headers(100, 10));
        limiter.release();

        assertThatThrownBy(limiter::acquire)
                .isInstanceOfSatisfying(HypixelRateLimitException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(60));
    }

    @Test
    void headerlessThrottleUsesFallbackInsteadOfAnAlmostExpiredWindow() {
        limiter.update(headers(10, 1));
        assertThat(limiter.rejectedByUpstream(new HttpHeaders()).getRetryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void permitsRequestsAfterTheWindowExpires() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        HypixelRateLimiter timedLimiter = new HypixelRateLimiter(clock);
        timedLimiter.update(headers(0, 30));
        assertThatThrownBy(timedLimiter::acquire).isInstanceOf(HypixelRateLimitException.class);

        when(clock.instant()).thenReturn(now.plusSeconds(30));
        assertThatCode(timedLimiter::acquire).doesNotThrowAnyException();
        timedLimiter.update(headers(2, 60));
        timedLimiter.release();
        timedLimiter.acquire();
        timedLimiter.release();
        timedLimiter.acquire();
        timedLimiter.release();
        assertThatThrownBy(timedLimiter::acquire).isInstanceOf(HypixelRateLimitException.class);
    }

    private HttpHeaders headers(int remaining, int resetSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("RateLimit-Remaining", Integer.toString(remaining));
        headers.set("RateLimit-Reset", Integer.toString(resetSeconds));
        return headers;
    }
}
