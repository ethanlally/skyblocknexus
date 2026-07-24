package com.ethanlally.skyblocknexus.hypixel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class HypixelResponseCacheTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void expiresEntriesAfterTheConfiguredTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-23T12:00:00Z"));
        HypixelResponseCache cache =
                new HypixelResponseCache(Duration.ofSeconds(30), 10, clock);
        JsonNode response = objectMapper.createObjectNode().put("success", true);

        cache.put("player", response);

        assertThat(cache.get("player")).isSameAs(response);
        clock.advance(Duration.ofSeconds(30));
        assertThat(cache.get("player")).isNull();
    }

    @Test
    void removesTheLeastRecentlyUsedEntryAtCapacity() {
        HypixelResponseCache cache = new HypixelResponseCache(
                Duration.ofMinutes(1),
                2,
                Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneOffset.UTC));
        JsonNode first = objectMapper.createObjectNode().put("id", 1);
        JsonNode second = objectMapper.createObjectNode().put("id", 2);
        JsonNode third = objectMapper.createObjectNode().put("id", 3);

        cache.put("first", first);
        cache.put("second", second);
        assertThat(cache.get("first")).isSameAs(first);
        cache.put("third", third);

        assertThat(cache.get("first")).isSameAs(first);
        assertThat(cache.get("second")).isNull();
        assertThat(cache.get("third")).isSameAs(third);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
