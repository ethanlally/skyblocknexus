package com.ethanlally.skyblocknexus.hypixel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class HypixelRateLimitHandlerTests {

    @Test
    void returnsRetryTimingToTheClient() {
        var response = new HypixelRateLimitHandler()
                .handleRateLimit(new HypixelRateLimitException(17));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("17");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().retryAfterSeconds()).isEqualTo(17);
        assertThat(response.getBody().message()).contains("17 seconds");
    }
}
