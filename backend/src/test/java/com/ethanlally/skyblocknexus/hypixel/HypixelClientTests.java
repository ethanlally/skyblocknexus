package com.ethanlally.skyblocknexus.hypixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HypixelClientTests {

    @Test
    void readsAPlayerFromTheHypixelResponse() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HypixelClient client = new HypixelClient("test-key", builder
                .baseUrl("https://api.hypixel.net")
                .build(), new HypixelRateLimiter());
        String fixture = new ClassPathResource("fixtures/hypixel/player-success.json")
                .getContentAsString(StandardCharsets.UTF_8);

        server.expect(queryParam("uuid", "0123456789abcdef0123456789abcdef"))
                .andExpect(header("API-Key", "test-key"))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        var player = client.getPlayer("0123456789abcdef0123456789abcdef");

        assertThat(player.displayName()).isEqualTo("ExamplePlayer");
        assertThat(player.lastLogin()).isEqualTo(1711929600000L);
        server.verify();
    }

    @Test
    void stopsBeforeAnotherRequestWhenTheKnownLimitIsExhausted() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HypixelRateLimiter rateLimiter = rateLimiterAt("2026-07-15T12:00:00Z");
        HypixelClient client = new HypixelClient("test-key", builder
                .baseUrl("https://api.hypixel.net")
                .build(), rateLimiter);
        String fixture = new ClassPathResource("fixtures/hypixel/player-success.json")
                .getContentAsString(StandardCharsets.UTF_8);

        server.expect(queryParam("uuid", "first-player"))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON)
                        .header("RateLimit-Limit", "120")
                        .header("RateLimit-Remaining", "0")
                        .header("RateLimit-Reset", "30"));

        client.getPlayer("first-player");

        assertThatThrownBy(() -> client.getPlayer("second-player"))
                .isInstanceOf(HypixelRateLimitException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(30L);
        server.verify();
    }

    @Test
    void startsALocalCooldownAfterAnUpstreamRateLimitResponse() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HypixelRateLimiter rateLimiter = rateLimiterAt("2026-07-15T12:00:00Z");
        HypixelClient client = new HypixelClient("test-key", builder
                .baseUrl("https://api.hypixel.net")
                .build(), rateLimiter);
        String fixture = new ClassPathResource("fixtures/hypixel/rate-limit.json")
                .getContentAsString(StandardCharsets.UTF_8);

        server.expect(queryParam("uuid", "first-player"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fixture)
                        .header("RateLimit-Limit", "120")
                        .header("RateLimit-Remaining", "0")
                        .header("RateLimit-Reset", "20"));

        assertThatThrownBy(() -> client.getPlayer("first-player"))
                .isInstanceOf(HypixelRateLimitException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(20L);
        assertThatThrownBy(() -> client.getPlayer("second-player"))
                .isInstanceOf(HypixelRateLimitException.class);
        server.verify();
    }

    private HypixelRateLimiter rateLimiterAt(String instant) {
        return new HypixelRateLimiter(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
